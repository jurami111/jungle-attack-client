package cr.ac.tec.jungleattack.client.model;

import java.util.Locale;
import java.util.Optional;

public enum AnimalSpecies {
    MONO, MARIPOSA, RANA, PEREZOSO, SERPIENTE, TORTUGA, YIGUIRRO;

    public String spriteName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AnimalSpecies> find(String name) {
        return java.util.Arrays.stream(values()).filter(value -> value.name().equals(name)).findFirst();
    }
}
