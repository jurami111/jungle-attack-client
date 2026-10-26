package cr.ac.tec.jungleattack.client.network;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;

public final class ServerMessageParser {
    private static final BigDecimal MIN_INTEGER = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal MAX_INTEGER = BigDecimal.valueOf(Integer.MAX_VALUE);
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    public ParseResult parse(String line) {
        if (line == null || line.isBlank()) {
            return failure("El mensaje JSON esta vacio");
        }
        try {
            JsonElement root = gson.fromJson(line, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return failure("El mensaje debe ser un objeto JSON");
            }
            JsonObject message = root.getAsJsonObject();
            if (!text(message.get("type")) || !message.has("payload")
                    || !message.get("payload").isJsonObject()) {
                return failure("El mensaje requiere type de texto y payload de objeto");
            }
            String type = message.get("type").getAsString();
            JsonObject payload = message.getAsJsonObject("payload");
            Class<? extends Payload> payloadClass = payloadClass(type);
            if (payloadClass == null) {
                return failure("Tipo de mensaje no soportado: " + type);
            }
            if (!validPayload(type, payload)) {
                return failure("Campos ausentes, tipos incorrectos o datos inconsistentes en payload de " + type);
            }
            Payload data = gson.fromJson(payload, payloadClass);
            return new ParseResult(Optional.of(new ServerMessage(type, data)), Optional.empty());
        } catch (JsonParseException exception) {
            return failure("No se pudo interpretar el JSON: " + exception.getMessage());
        }
    }

    private static Class<? extends Payload> payloadClass(String type) {
        return switch (type) {
            case "MATCH_CREATED", "MATCH_JOINED" -> MatchCode.class;
            case "PLAYER_JOINED" -> PlayerJoined.class;
            case "ANIMAL_SELECTION" -> AnimalSelection.class;
            case "GAME_STARTED", "STATE_UPDATE" -> Snapshot.class;
            case "GAME_OVER" -> GameOver.class;
            case "MATCH_CANCELLED" -> MatchCancelled.class;
            case "ERROR" -> ServerError.class;
            default -> null;
        };
    }

    private static boolean validPayload(String type, JsonObject payload) {
        return switch (type) {
            case "MATCH_CREATED", "MATCH_JOINED" -> code(payload.get("code"));
            case "PLAYER_JOINED" -> text(payload.get("nickname"));
            case "ANIMAL_SELECTION" -> array(payload.get("availableSpecies"), ServerMessageParser::text)
                    && nullableText(payload, "yourSpecies") && nullableText(payload, "opponentSpecies");
            case "GAME_STARTED", "STATE_UPDATE" -> snapshot(payload);
            case "GAME_OVER" -> object(payload, "winner", "durationSeconds", "")
                    && array(payload.get("packs"), value -> object(value, "species",
                            "botsKilled animalsEliminated cupsConsumed", ""));
            case "MATCH_CANCELLED" -> text(payload.get("reason"));
            case "ERROR" -> text(payload.get("message"));
            default -> false;
        };
    }

    private static boolean snapshot(JsonObject payload) {
        if (!object(payload, "ownFlagColor", "", "running") || !code(payload.get("matchCode"))
                || !object(payload.get("ownPack"), "species",
                        "damageCapacity botsKilled animalsEliminated cupsConsumed", "")
                || !array(payload.get("ownAnimals"), value -> animal(value, false))
                || !array(payload.get("visibleAnimals"), value -> animal(value, true))
                || !array(payload.get("visibleZones"), ServerMessageParser::zone)
                || !array(payload.get("visibleCoffeeCups"), value -> object(value, "", "x y", ""))
                || !array(payload.get("visibleFlags"),
                        value -> object(value, "owner color", "x y", "captured"))
                || !payload.has("selectedZone")) {
            return false;
        }
        Set<Integer> ids = new HashSet<>();
        for (String list : new String[]{"ownAnimals", "visibleAnimals"}) {
            for (JsonElement animal : payload.getAsJsonArray(list)) {
                if (!ids.add(animal.getAsJsonObject().get("id").getAsInt())) {
                    return false;
                }
            }
        }
        Set<String> names = new HashSet<>();
        for (JsonElement zone : payload.getAsJsonArray("visibleZones")) {
            if (!names.add(zone.getAsJsonObject().get("name").getAsString())) {
                return false;
            }
        }
        JsonElement selected = payload.get("selectedZone");
        if (names.isEmpty()) {
            return selected.isJsonNull() && payload.getAsJsonArray("visibleAnimals").isEmpty()
                    && payload.getAsJsonArray("visibleCoffeeCups").isEmpty()
                    && payload.getAsJsonArray("visibleFlags").isEmpty();
        }
        return zone(selected) && payload.getAsJsonArray("visibleZones").asList().stream()
                .anyMatch(selected::equals);
    }

    private static boolean animal(JsonElement value, boolean visible) {
        if (!object(value, "species", "id x y energy", "alive")) {
            return false;
        }
        JsonObject animal = value.getAsJsonObject();
        if (animal.has("attack") && !animal.get("attack").isJsonNull()
                && (!object(animal.get("attack"), "", "sequence targetX targetY", "")
                    || animal.getAsJsonObject("attack").get("sequence").getAsInt() <= 0)) {
            return false;
        }
        return visible ? bool(animal.get("bot"))
                : !animal.has("bot") || bool(animal.get("bot"));
    }

    private static boolean zone(JsonElement value) {
        return object(value, "name", "centerX centerY", "");
    }

    // Se validan los tipos antes de Gson para impedir coerciones como "20" a numero.
    private static boolean object(JsonElement value, String strings, String integers, String booleans) {
        if (value == null || !value.isJsonObject()) {
            return false;
        }
        JsonObject object = value.getAsJsonObject();
        return fields(object, strings, ServerMessageParser::text)
                && fields(object, integers, ServerMessageParser::integer)
                && fields(object, booleans, ServerMessageParser::bool);
    }

    private static boolean fields(JsonObject object, String names, Predicate<JsonElement> check) {
        if (names.isEmpty()) {
            return true;
        }
        for (String name : names.split(" ")) {
            if (!check.test(object.get(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean array(JsonElement value, Predicate<JsonElement> check) {
        return value != null && value.isJsonArray()
                && value.getAsJsonArray().asList().stream().allMatch(check);
    }

    private static boolean text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !value.getAsString().isBlank();
    }

    private static boolean nullableText(JsonObject object, String name) {
        return object.has(name) && (object.get(name).isJsonNull() || text(object.get(name)));
    }

    private static boolean code(JsonElement value) {
        return text(value) && value.getAsString().matches("[A-Za-z0-9]{4}");
    }

    private static boolean bool(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            BigDecimal number = value.getAsBigDecimal();
            return number.compareTo(MIN_INTEGER) >= 0 && number.compareTo(MAX_INTEGER) <= 0
                    && number.stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static ParseResult failure(String error) {
        return new ParseResult(Optional.empty(), Optional.of(error));
    }

    public record ParseResult(Optional<ServerMessage> message, Optional<String> error) {
    }
}
