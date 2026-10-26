package cr.ac.tec.jungleattack.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JsonParseTest {
    private final ServerMessageParser parser = new ServerMessageParser();

    private Map<String, String> examples() throws IOException {
        var stream = getClass().getResourceAsStream("/protocol/server-messages.jsonl");
        assertNotNull(stream);
        Map<String, String> examples = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line : reader.lines().toList()) {
                String type = JsonParser.parseString(line).getAsJsonObject().get("type").getAsString();
                assertNull(examples.put(type, line));
            }
        }
        return examples;
    }

    private ServerMessage read(String json) {
        var result = parser.parse(json);
        assertTrue(result.error().isEmpty(), () -> result.error().orElse(""));
        return result.message().orElseThrow();
    }

    private void rejects(String json) {
        var result = parser.parse(json);
        assertTrue(result.message().isEmpty());
        assertTrue(result.error().isPresent());
        assertFalse(result.error().orElseThrow().isBlank());
    }

    @Test
    void parsesAllNineServerMessageTypesIntoTheirPayloads() throws IOException {
        var examples = examples();
        assertEquals(Set.of("MATCH_CREATED", "MATCH_JOINED", "PLAYER_JOINED", "ANIMAL_SELECTION",
                "GAME_STARTED", "STATE_UPDATE", "GAME_OVER", "MATCH_CANCELLED", "ERROR"), examples.keySet());
        for (var entry : examples.entrySet()) {
            assertEquals(entry.getKey(), read(entry.getValue()).type());
        }
        assertEquals("A7K2", assertInstanceOf(MatchCode.class,
                read(examples.get("MATCH_CREATED")).payload()).code());
        assertEquals("A7K2", assertInstanceOf(MatchCode.class,
                read(examples.get("MATCH_JOINED")).payload()).code());
        assertEquals("Luis", assertInstanceOf(PlayerJoined.class,
                read(examples.get("PLAYER_JOINED")).payload()).nickname());
        var selection = assertInstanceOf(AnimalSelection.class,
                read(examples.get("ANIMAL_SELECTION")).payload());
        assertEquals(6, selection.availableSpecies().size());
        assertEquals("MONO", selection.yourSpecies());
        assertNull(selection.opponentSpecies());
        var over = assertInstanceOf(GameOver.class, read(examples.get("GAME_OVER")).payload());
        assertEquals("MONO", over.winner());
        assertEquals(180, over.durationSeconds());
        assertEquals(7, over.packs().size());
        assertEquals(2, over.packs().getFirst().botsKilled());
        assertEquals("The other player disconnected", assertInstanceOf(MatchCancelled.class,
                read(examples.get("MATCH_CANCELLED")).payload()).reason());
        assertEquals("The other player already chose that species", assertInstanceOf(ServerError.class,
                read(examples.get("ERROR")).payload()).message());
    }

    @Test
    void preservesSnapshotFieldsAndDoesNotAccumulateEarlierLists() throws IOException {
        var examples = examples();
        var initial = assertInstanceOf(Snapshot.class, read(examples.get("GAME_STARTED")).payload());
        var update = assertInstanceOf(Snapshot.class, read(examples.get("STATE_UPDATE")).payload());
        assertEquals("A7K2", update.matchCode());
        assertTrue(update.running());
        assertEquals("MONO", update.ownPack().species());
        assertEquals(3, update.ownPack().damageCapacity());
        assertEquals(1, update.ownPack().cupsConsumed());
        assertEquals("#E53935", update.ownFlagColor());
        assertEquals(2, update.ownAnimals().size());
        var own = update.ownAnimals().getFirst();
        assertEquals(1, own.id());
        assertEquals(440, own.x());
        assertEquals(400, own.y());
        assertEquals(19, own.energy());
        assertTrue(own.alive());
        assertNull(own.bot());
        var target = update.visibleAnimals().getFirst();
        assertEquals(21, target.id());
        assertEquals("RANA", target.species());
        assertEquals(7, target.energy());
        assertEquals(Boolean.TRUE, target.bot());
        assertEquals("Playa Conchal", update.selectedZone().name());
        assertEquals(331, update.visibleZones().getFirst().centerX());
        assertEquals(327, update.selectedZone().centerY());
        assertEquals(450, initial.visibleCoffeeCups().getFirst().x());
        assertEquals(400, initial.visibleCoffeeCups().getFirst().y());
        assertTrue(update.visibleCoffeeCups().isEmpty());
        assertTrue(initial.visibleAnimals().isEmpty());
        var flag = update.visibleFlags().getFirst();
        assertEquals("MONO", flag.owner());
        assertEquals(331, flag.x());
        assertEquals(327, flag.y());
        assertEquals("#E53935", flag.color());
        assertFalse(flag.captured());
        assertEquals(20, initial.ownAnimals().getFirst().energy());
        assertThrows(UnsupportedOperationException.class, () -> update.ownAnimals().clear());
        assertThrows(UnsupportedOperationException.class, () -> update.visibleZones().clear());
    }

    @Test
    void acceptsOptionalAttackEventsButRejectsMalformedEvents() throws IOException {
        var message = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        JsonObject animal = message.getAsJsonObject("payload").getAsJsonArray("visibleAnimals")
                .get(0).getAsJsonObject();
        assertNull(((Snapshot) read(message.toString()).payload()).visibleAnimals().getFirst().attack());
        animal.add("attack", JsonParser.parseString("{\"sequence\":1,\"targetX\":440,\"targetY\":400}"));
        Attack attack = ((Snapshot) read(message.toString()).payload()).visibleAnimals().getFirst().attack();
        assertEquals(new Attack(1, 440, 400), attack);
        animal.getAsJsonObject("attack").addProperty("sequence", 0);
        rejects(message.toString());
        animal.getAsJsonObject("attack").addProperty("sequence", 1);
        animal.getAsJsonObject("attack").addProperty("targetX", "440");
        rejects(message.toString());
        animal.add("attack", JsonNull.INSTANCE);
        assertNull(((Snapshot) read(message.toString()).payload()).visibleAnimals().getFirst().attack());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{", "null", "[]", "{}", "true",
            "{\"type\":\"ERROR\",\"payload\":{}}",
            "{\"type\":1,\"payload\":{}}",
            "{\"type\":\"ERROR\",\"payload\":null}",
            "{\"type\":\"ERROR\",\"payload\":[]}",
            "{\"type\":\"UNKNOWN\",\"payload\":{}}",
            "{\"type\":\"ERROR\",\"payload\":{\"message\":false}}",
            "{\"type\":\"ERROR\",\"payload\":{\"message\":\" \"}}",
            "{\"type\":\"ERROR\",\"payload\":{\"message\":\"x\"}} trailing",
            "{\"type\":\"ERROR\",\"payload\":{\"message\":\"x\"}} {}",
            "{/*comment*/\"type\":\"ERROR\",\"payload\":{\"message\":\"x\"}}",
            "{\"type\":\"ERROR\",\"payload\":{\"message\":\"x\",}}"})
    void rejectsInvalidMessagesWithAnExplicitError(String json) {
        rejects(json);
    }

    @Test
    void readsReorderedFieldsUtf8AndUnknownAdditionalFields() {
        var message = read("""
                {"payload":{"message":"Conexi\u00f3n perdida","extra":true},"type":"ERROR","version":1}
                """);
        assertEquals("Conexi\u00f3n perdida", assertInstanceOf(ServerError.class, message.payload()).message());
    }

    @Test
    void missingSnapshotFieldsAreNotReplacedByJavaDefaults() throws IOException {
        var snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        for (String name : snapshot.getAsJsonObject("payload").keySet()) {
            var incomplete = snapshot.deepCopy();
            incomplete.getAsJsonObject("payload").remove(name);
            rejects(incomplete.toString());
        }
        var incomplete = snapshot.deepCopy();
        incomplete.getAsJsonObject("payload").getAsJsonArray("ownAnimals")
                .get(0).getAsJsonObject().remove("alive");
        rejects(incomplete.toString());
        incomplete = snapshot.deepCopy();
        incomplete.getAsJsonObject("payload").getAsJsonArray("visibleAnimals")
                .get(0).getAsJsonObject().remove("bot");
        rejects(incomplete.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"19\"", "19.5", "2147483648", "-2147483649", "true", "null", "{}", "[]"})
    void rejectsCoercedFractionalOrOutOfRangeIntegers(String value) throws IOException {
        var snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        snapshot.getAsJsonObject("payload").getAsJsonArray("ownAnimals").get(0).getAsJsonObject()
                .add("energy", JsonParser.parseString(value));
        rejects(snapshot.toString());
    }

    @Test
    void acceptsEmptyVisibilityAndRejectsInconsistentSelectedZone() throws IOException {
        var snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        var payload = snapshot.getAsJsonObject("payload");
        payload.add("visibleZones", new JsonArray());
        rejects(snapshot.toString());
        payload.add("selectedZone", JsonNull.INSTANCE);
        rejects(snapshot.toString());
        payload.add("visibleAnimals", new JsonArray());
        payload.add("visibleCoffeeCups", new JsonArray());
        payload.add("visibleFlags", new JsonArray());
        var empty = assertInstanceOf(Snapshot.class, read(snapshot.toString()).payload());
        assertNull(empty.selectedZone());
        assertTrue(empty.visibleZones().isEmpty());
        assertTrue(empty.visibleAnimals().isEmpty());
        var original = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        original.getAsJsonObject("payload").add("selectedZone", JsonNull.INSTANCE);
        rejects(original.toString());
        original = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        original.getAsJsonObject("payload").getAsJsonObject("selectedZone").addProperty("name", "Otra zona");
        rejects(original.toString());
    }

    @Test
    void rejectsDuplicateAnimalIdsAndZoneNames() throws IOException {
        JsonObject snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        snapshot.getAsJsonObject("payload").getAsJsonArray("visibleAnimals").get(0)
                .getAsJsonObject().addProperty("id", 1);
        rejects(snapshot.toString());
        snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        var zones = snapshot.getAsJsonObject("payload").getAsJsonArray("visibleZones");
        zones.add(zones.get(0).deepCopy());
        rejects(snapshot.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownAnimals", "visibleAnimals", "visibleZones",
            "visibleCoffeeCups", "visibleFlags"})
    void rejectsWrongArrayTypesAndNullEntries(String name) throws IOException {
        var snapshot = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        snapshot.getAsJsonObject("payload").add(name, new JsonObject());
        rejects(snapshot.toString());
        var array = new JsonArray();
        array.add(JsonNull.INSTANCE);
        snapshot.getAsJsonObject("payload").add(name, array);
        rejects(snapshot.toString());
    }

    @Test
    void rejectsMissingNestedFieldsAndBooleanCoercions() throws IOException {
        var example = JsonParser.parseString(examples().get("GAME_STARTED")).getAsJsonObject();
        for (String list : new String[]{"ownAnimals", "visibleZones", "visibleCoffeeCups", "visibleFlags"}) {
            var object = example.getAsJsonObject("payload").getAsJsonArray(list).get(0).getAsJsonObject();
            for (String field : object.keySet()) {
                var changed = example.deepCopy();
                changed.getAsJsonObject("payload").getAsJsonArray(list).get(0).getAsJsonObject().remove(field);
                rejects(changed.toString());
            }
        }
        var pack = example.getAsJsonObject("payload").getAsJsonObject("ownPack");
        for (String field : pack.keySet()) {
            var changed = example.deepCopy();
            changed.getAsJsonObject("payload").getAsJsonObject("ownPack").remove(field);
            rejects(changed.toString());
        }
        example.getAsJsonObject("payload").addProperty("running", "true");
        rejects(example.toString());
        example = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        example.getAsJsonObject("payload").getAsJsonArray("visibleAnimals").get(0)
                .getAsJsonObject().addProperty("bot", "false");
        rejects(example.toString());
    }

    @Test
    void acceptsDeadOwnAnimalsFalseBotAndStoppedGameWithoutChangingValues() throws IOException {
        var example = JsonParser.parseString(examples().get("STATE_UPDATE")).getAsJsonObject();
        var payload = example.getAsJsonObject("payload");
        payload.addProperty("running", false);
        var own = payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject();
        own.addProperty("energy", 0);
        own.addProperty("alive", false);
        payload.getAsJsonArray("visibleAnimals").get(0).getAsJsonObject().addProperty("bot", false);
        var snapshot = assertInstanceOf(Snapshot.class, read(example.toString()).payload());
        assertFalse(snapshot.running());
        assertFalse(snapshot.ownAnimals().getFirst().alive());
        assertEquals(0, snapshot.ownAnimals().getFirst().energy());
        assertEquals(Boolean.FALSE, snapshot.visibleAnimals().getFirst().bot());
    }

    @Test
    void validatesSelectionSummaryAndLobbyPayloads() throws IOException {
        var examples = examples();
        var selection = JsonParser.parseString(examples.get("ANIMAL_SELECTION")).getAsJsonObject();
        selection.getAsJsonObject("payload").add("yourSpecies", JsonNull.INSTANCE);
        assertNull(assertInstanceOf(AnimalSelection.class, read(selection.toString()).payload()).yourSpecies());
        selection.getAsJsonObject("payload").remove("opponentSpecies");
        rejects(selection.toString());
        selection = JsonParser.parseString(examples.get("ANIMAL_SELECTION")).getAsJsonObject();
        selection.getAsJsonObject("payload").getAsJsonArray("availableSpecies").add(123);
        rejects(selection.toString());
        var over = JsonParser.parseString(examples.get("GAME_OVER")).getAsJsonObject();
        over.getAsJsonObject("payload").getAsJsonArray("packs").get(0).getAsJsonObject().remove("botsKilled");
        rejects(over.toString());
        over = JsonParser.parseString(examples.get("GAME_OVER")).getAsJsonObject();
        over.getAsJsonObject("payload").addProperty("durationSeconds", 1.5);
        rejects(over.toString());
        rejects("{\"type\":\"MATCH_CREATED\",\"payload\":{\"code\":\"ABC\"}}");
        rejects("{\"type\":\"MATCH_JOINED\",\"payload\":{\"code\":1234}}");
        rejects("{\"type\":\"PLAYER_JOINED\",\"payload\":{\"nickname\":null}}");
        rejects("{\"type\":\"MATCH_CANCELLED\",\"payload\":{\"reason\":false}}");
    }
}