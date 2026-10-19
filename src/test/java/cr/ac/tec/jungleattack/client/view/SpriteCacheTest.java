package cr.ac.tec.jungleattack.client.view;

import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteCacheTest {
    private static final SpriteCache cache = new SpriteCache();

    @Test
    void loadsAllEightSvgResourcesAndReusesImages() {
        assertEquals(8, SpriteCache.ASSET_NAMES.size());
        for (String name : SpriteCache.ASSET_NAMES) {
            BufferedImage image = cache.getImage(name).orElse(null);
            assertNotNull(image, name);
            assertTrue(image.getWidth() > 0, name);
            assertTrue(image.getHeight() > 0, name);
            assertTrue(image.getColorModel().hasAlpha(), name);
            assertSame(image, cache.getImage(name).orElse(null), name);
        }
    }

    @Test
    void preservesAnimalTransparencyAtTopLeftCorner() {
        for (String name : SpriteCache.ASSET_NAMES) {
            if (!name.equals("Mapa.svg")) {
                BufferedImage image = cache.getImage(name).orElse(null);
                assertNotNull(image, name);
                assertEquals(0, image.getRGB(0, 0) >>> 24, name);
            }
        }
    }

    @Test
    void preservesOriginalMapDimensionsAndOpaqueCorner() {
        BufferedImage map = cache.getImage("Mapa.svg").orElse(null);
        assertNotNull(map);
        assertEquals(2400, map.getWidth());
        assertEquals(1792, map.getHeight());
        assertEquals(255, map.getRGB(0, 0) >>> 24);
    }

    @Test
    void returnsEmptyForUnknownImageNames() {
        assertFalse(cache.getImage("missing.svg").isPresent());
    }

    @Test
    void reportsNoErrorsWhenAllAssetsLoad() {
        assertTrue(cache.getLoadErrors().isEmpty());
    }

    @Test
    void loadsAllThirtyNormalizedFrames() {
        String[] animals = {"mariposa", "mono", "perezoso", "rana", "serpiente", "tortuga", "yiguirro"};
        int[] counts = {2, 5, 6, 5, 4, 4, 4};
        int total = 0;
        for (int i = 0; i < animals.length; i++) {
            for (int frame = 1; frame <= counts[i]; frame++) {
                String name = "normalized/" + animals[i] + "/" + animals[i] + "-" + frame + ".svg";
                BufferedImage image = cache.getImage(name).orElse(null);
                assertNotNull(image, name);
                assertEquals(512, image.getWidth(), name);
                assertEquals(512, image.getHeight(), name);
                assertTrue(image.getColorModel().hasAlpha(), name);
                assertEquals(0, image.getRGB(0, 0) >>> 24, name);
                assertEquals(0, image.getRGB(511, 0) >>> 24, name);
                assertEquals(0, image.getRGB(0, 511) >>> 24, name);
                assertEquals(0, image.getRGB(511, 511) >>> 24, name);
                assertSame(image, cache.getImage(name).orElse(null), name);
                total++;
            }
        }
        assertEquals(30, total);
    }

    @Test
    void preparesAllSequencesUsingCachedImagesInManifestOrder() {
        String[] animals = {"mariposa", "mono", "perezoso", "rana", "serpiente", "tortuga", "yiguirro"};
        int[][] sequences = {{1, 2}, {2, 3, 4, 5}, {2, 3, 4, 5, 6},
                {2, 3, 4, 5}, {2, 3, 4, 3}, {2, 3, 4, 3}, {1, 2, 3, 4, 3, 2}};
        int[] durations = {150, 150, 250, 150, 180, 180, 100};
        for (int i = 0; i < animals.length; i++) {
            var animation = cache.getAnimation(animals[i]).orElse(null);
            assertNotNull(animation, animals[i]);
            assertEquals(durations[i], animation.frameDurationMs());
            assertEquals(sequences[i].length, animation.frames().size());
            String prefix = "normalized/" + animals[i] + "/" + animals[i] + "-";
            assertSame(cache.getImage(prefix + "1.svg").orElse(null), animation.idleImage());
            for (int j = 0; j < sequences[i].length; j++) {
                assertSame(cache.getImage(prefix + sequences[i][j] + ".svg").orElse(null),
                        animation.frames().get(j));
            }
            assertSame(animation, cache.getAnimation(animals[i]).orElse(null));
        }
        assertTrue(cache.getAnimation("unknown").isEmpty());
    }
}