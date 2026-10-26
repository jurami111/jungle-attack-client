 package cr.ac.tec.jungleattack.client.model.dto;

import java.util.List;

public record ServerMessage(String type, Payload payload) {
    public sealed interface Payload permits MatchCode, PlayerJoined, AnimalSelection,
            Snapshot, GameOver, MatchCancelled, ServerError {
    }

    public record MatchCode(String code) implements Payload {
    }

    public record PlayerJoined(String nickname) implements Payload {
    }

    public record AnimalSelection(List<String> availableSpecies, String yourSpecies,
            String opponentSpecies) implements Payload {
        public AnimalSelection {
            availableSpecies = List.copyOf(availableSpecies);
        }
    }

    public record Snapshot(String matchCode, boolean running, Pack ownPack, String ownFlagColor,
            List<Animal> ownAnimals, List<Zone> visibleZones, Zone selectedZone,
            List<Animal> visibleAnimals, List<CoffeeCup> visibleCoffeeCups,
            List<Flag> visibleFlags) implements Payload {
        public Snapshot {
            ownAnimals = List.copyOf(ownAnimals);
            visibleZones = List.copyOf(visibleZones);
            visibleAnimals = List.copyOf(visibleAnimals);
            visibleCoffeeCups = List.copyOf(visibleCoffeeCups);
            visibleFlags = List.copyOf(visibleFlags);
        }
    }

    public record Pack(String species, int damageCapacity, int botsKilled,
            int animalsEliminated, int cupsConsumed) {
    }

    // bot no se envia para los animales propios; no se sustituye su ausencia por false.
    public record Animal(int id, String species, int x, int y, int energy, boolean alive, Boolean bot,
            Attack attack) {
        public Animal(int id, String species, int x, int y, int energy, boolean alive, Boolean bot) {
            this(id, species, x, y, energy, alive, bot, null);
        }
    }

    public record Attack(int sequence, int targetX, int targetY) {
    }

    public record Zone(String name, int centerX, int centerY) {
    }

    public record CoffeeCup(int x, int y) {
    }

    public record Flag(String owner, int x, int y, String color, boolean captured) {
    }

    public record GameOver(String winner, int durationSeconds, List<PackSummary> packs) implements Payload {
        public GameOver {
            packs = List.copyOf(packs);
        }
    }

    public record PackSummary(String species, int botsKilled, int animalsEliminated, int cupsConsumed) {
    }

    public record MatchCancelled(String reason) implements Payload {
    }

    public record ServerError(String message) implements Payload {
    }
}
