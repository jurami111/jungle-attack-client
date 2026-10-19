package cr.ac.tec.jungleattack.client.view.utils;// clase que representa la animación de un sprite del juego

import java.awt.image.BufferedImage;
import java.util.List;

public record SpriteAnimation(
        BufferedImage idleImage,
        List<BufferedImage> frames,
        int frameDurationMs) {

    public SpriteAnimation {
        frames = List.copyOf(frames);
    }
}
