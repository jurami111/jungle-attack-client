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
}