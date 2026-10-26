package cr.ac.tec.jungleattack.client.view;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import cr.ac.tec.jungleattack.client.model.AnimalSpecies;
import cr.ac.tec.jungleattack.client.model.MapZones;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;
import static org.junit.jupiter.api.Assertions.*;

class AttackAnimationTest {
    private static final SpriteCache CACHE = new SpriteCache();

    private static Snapshot snapshot(String species, Attack attack) {
        Zone zone = MapZones.ALL.getFirst();
        return new Snapshot("TEST", true, new Pack("MONO", 2, 0, 0, 0), "#E53935",
                List.of(), List.of(zone), zone,
                List.of(new Animal(21, species, 400, 400, 10, true, true, attack)), List.of(), List.of());
    }

    private static BufferedImage paint(MapPanel panel) {
        BufferedImage image = new BufferedImage(2400, 1792, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static boolean differs(BufferedImage left, BufferedImage right) {
        for (int y = 378; y < 423; y++) {
            for (int x = 378; x < 444; x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) return true;
            }
        }
        return false;
    }

    @ParameterizedTest
    @EnumSource(AnimalSpecies.class)
    void stationaryBotsAnimateOnlyConfirmedAttacksAndDoNotRestartEverySnapshot(AnimalSpecies species)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong(1);
            MapPanel panel = new MapPanel(CACHE, false, clock::get);
            panel.setSize(2400, 1792);
            panel.showSnapshot(snapshot(species.name(), null));
            BufferedImage idle = paint(panel);
            Attack attack = new Attack(1, 425, 400);
            panel.showSnapshot(snapshot(species.name(), attack));
            clock.addAndGet(150_000_000);
            panel.showSnapshot(snapshot(species.name(), attack));
            assertTrue(differs(idle, paint(panel)), "El ataque debe verse aunque el bot no se mueva");
            clock.addAndGet(160_000_000);
            panel.showSnapshot(snapshot(species.name(), attack));
            assertFalse(differs(idle, paint(panel)), "El mismo ataque no reinicia en cada tick");
            panel.showSnapshot(snapshot(species.name(), new Attack(2, 425, 400)));
            clock.addAndGet(150_000_000);
            assertTrue(differs(idle, paint(panel)), "El siguiente golpe inicia otra animacion");
            panel.clearGame();
            panel.showSnapshot(snapshot(species.name(), null));
            assertFalse(differs(idle, paint(panel)));
            panel.removeNotify();
        });
    }
}
