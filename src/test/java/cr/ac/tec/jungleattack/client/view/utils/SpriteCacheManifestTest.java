package cr.ac.tec.jungleattack.client.view.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpriteCacheManifestTest {
    private SpriteCache fromJson(String json) {
        return new SpriteCache(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private String animal(String id, String idle, int duration, String sequence, String file) {
        return """
                {"id":"%s","idleFrame":%s,"frameDurationMs":%d,
                 "sequence":%s,"frames":[{"file":"%s"}]}
                """.formatted(id, idle, duration, sequence, file);
    }

    private String manifest(String animals) {
        return """
                {"canvasWidth":512,"canvasHeight":512,"animals":[%s]}
                """.formatted(animals);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}", "{\"canvasHeight\":512,\"animals\":[]}",
            "{\"canvasWidth\":512,\"canvasHeight\":512,\"animals\":[]} trailing"})
    void reportsMalformedOrIncompleteManifestAndKeepsBaseImages(String json) {
        var cache = fromJson(json);
        assertTrue(cache.getLoadErrors().containsKey("normalized/sequences.json"));
        assertTrue(cache.getImage("Mapa.svg").isPresent());
        assertTrue(cache.getAnimation("mono").isEmpty());
    }

    @Test
    void reportsMissingManifest() {
        var cache = new SpriteCache(null);
        assertTrue(cache.getLoadErrors().containsKey("normalized/sequences.json"));
        assertTrue(cache.getImage("Mapa.svg").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "{\"canvasWidth\":512,\"canvasHeight\":512,\"animals\":{}}",
            "{\"canvasWidth\":\"incorrecto\",\"canvasHeight\":512,\"animals\":[]}",
            "{\"canvasWidth\":512,\"canvasHeight\":512,\"animals\":[],}",
            "{/* comentario */\"canvasWidth\":512,\"canvasHeight\":512,\"animals\":[]}"
    })
    void rejectsWrongJsonTypesAndNonstandardSyntax(String json) {
        var cache = fromJson(json);
        String error = cache.getLoadErrors().get("normalized/sequences.json");
        assertNotNull(error);
        assertFalse(error.isBlank());
        assertTrue(cache.getImage("Mapa.svg").isPresent());
        assertTrue(cache.getAnimation("mono").isEmpty());
    }

    @Test
    void parsesUtf8ManifestWithReorderedFieldsAndBuildsAnimation() {
        String json = """
                {
                  "animals": [{
                    "frames": [{"file": "mariposa/mariposa-1.svg"}],
                    "sequence": [1, 1],
                    "frameDurationMs": 125,
                    "idleFrame": 1,
                    "id": "mariposa-\u00e1"
                  }],
                  "canvasHeight": 512,
                  "canvasWidth": 512
                }
                """;
        var cache = fromJson(json);
        var animation = cache.getAnimation("mariposa-\u00e1").orElseThrow();
        assertTrue(cache.getLoadErrors().isEmpty());
        assertEquals(125, animation.frameDurationMs());
        assertEquals(2, animation.frames().size());
        assertSame(cache.getImage("normalized/mariposa/mariposa-1.svg").orElseThrow(),
                animation.idleImage());
        assertSame(animation.idleImage(), animation.frames().get(0));
        assertSame(animation.frames().get(0), animation.frames().get(1));
    }

    @Test
    void rejectsInvalidDefinitionsButStillLoadsValidAnimal() {
        String validFile = "mariposa/mariposa-1.svg";
        String definitions = String.join(",",
                "null",
                animal("", "1", 100, "[1]", validFile),
                animal("bad-idle", "0", 100, "[1]", validFile),
                animal("null-idle", "null", 100, "[1]", validFile),
                animal("bad-duration", "1", 0, "[1]", validFile),
                animal("bad-sequence", "1", 100, "[2]", validFile),
                animal("null-sequence", "1", 100, "[null]", validFile),
                animal("empty-sequence", "1", 100, "[]", validFile),
                animal("bad-path", "1", 100, "[1]", "../Mapa.svg"),
                animal("duplicate", "1", 100, "[1]", validFile),
                animal("duplicate", "1", 100, "[1]", validFile),
                animal("missing", "1", 100, "[1]", "mono/missing.svg"),
                animal("valid", "1", 100, "[1,1]", validFile));
        var cache = fromJson(manifest(definitions));
        assertEquals(13, cache.getLoadErrors().size());
        assertTrue(cache.getAnimation("duplicate").isEmpty());
        assertTrue(cache.getAnimation("missing").isEmpty());
        var animation = cache.getAnimation("valid").orElseThrow();
        assertSame(animation.frames().get(0), animation.frames().get(1));
        assertSame(animation.idleImage(), animation.frames().get(0));
    }

    @Test
    void reportsMismatchedCanvasInsteadOfPublishingAnimation() {
        var cache = fromJson(manifest(animal("mono", "1", 100, "[1]", "mono/mono-1.svg"))
                .replace("\"canvasWidth\":512", "\"canvasWidth\":64"));
        assertTrue(cache.getAnimation("mono").isEmpty());
        assertTrue(cache.getLoadErrors().containsKey("normalized/mono/mono-1.svg"));
    }
}
