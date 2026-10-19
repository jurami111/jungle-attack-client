package cr.ac.tec.jungleattack.client.view.utils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpriteAnimationTest {
    @Test
    void copiesFrameListWithoutCopyingImages() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var original = new ArrayList<>(List.of(image));
        var animation = new SpriteAnimation(image, original, 100);
        original.clear();
        assertEquals(1, animation.frames().size());
        assertSame(image, animation.frames().get(0));
        assertThrows(UnsupportedOperationException.class, () -> animation.frames().clear());
    }
}
