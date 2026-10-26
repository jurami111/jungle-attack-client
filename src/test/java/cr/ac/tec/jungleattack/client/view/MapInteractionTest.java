package cr.ac.tec.jungleattack.client.view;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.controller.GameController;
import cr.ac.tec.jungleattack.client.model.AnimalSpecies;
import cr.ac.tec.jungleattack.client.model.ClickableAnimal;
import cr.ac.tec.jungleattack.client.model.MapZones;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

import static org.junit.jupiter.api.Assertions.*;

class MapInteractionTest {
    private static final SpriteCache CACHE = new SpriteCache();

    private static Snapshot state(String species, int x, int y) {
        Zone zone = MapZones.ALL.getFirst();
        return new Snapshot("AB12", true, new Pack(species, 2, 0, 0, 0), "#E53935",
                List.of(new Animal(1, species, x, y, 20, true, null)),
                List.of(zone), zone, List.of(), List.of(), List.of());
    }

    private static void wheel(MapPanel panel, Point anchor, int turns) {
        panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, anchor.x, anchor.y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, turns));
    }

    private static BufferedImage paint(MapPanel panel) {
        BufferedImage result = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return result;
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void zoomKeepsCursorAnchorAndDrawingAlignedWithClickCommands(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE, false);
            panel.setSize(width, height);
            double baseScale = Math.min(width / 2400.0, height / 1792.0);
            Point anchor = new Point((int) Math.round((width - 2400 * baseScale) / 2 + 400 * baseScale),
                    (int) Math.round((height - 1792 * baseScale) / 2 + 400 * baseScale));
            Point2D.Double original = panel.toMapPoint(anchor).orElseThrow();
            wheel(panel, anchor, -4);
            Point2D.Double zoomed = panel.toMapPoint(anchor).orElseThrow();
            assertEquals(original.x, zoomed.x, 1e-8);
            assertEquals(original.y, zoomed.y, 1e-8);
            Point adjacent = new Point(anchor.x + 10, anchor.y);
            assertEquals(10 / (baseScale * Math.pow(UIConstants.MAP_ZOOM_STEP, 4)),
                    panel.toMapPoint(adjacent).orElseThrow().x - zoomed.x, 1e-8);
            panel.showSnapshot(state("TORTUGA", (int) original.x, (int) original.y));
            BufferedImage animal = paint(panel);
            panel.clearGame();
            wheel(panel, anchor, -4);
            Snapshot empty = state("TORTUGA", (int) original.x, (int) original.y);
            panel.showSnapshot(new Snapshot(empty.matchCode(), true, empty.ownPack(), empty.ownFlagColor(),
                    List.of(), empty.visibleZones(), empty.selectedZone(), List.of(), List.of(), List.of()));
            BufferedImage background = paint(panel);
            boolean changed = false;
            for (int y = anchor.y - 15; y < anchor.y + 15; y++) {
                for (int x = anchor.x - 15; x < anchor.x + 15; x++) {
                    changed |= animal.getRGB(x, y) != background.getRGB(x, y);
                }
            }
            assertTrue(changed, "El animal se dibuja donde se detectan los clics");
            GameController controller = new GameController(panel);
            List<String> sent = new ArrayList<>();
            controller.setMessageSender(sent::add);
            controller.updateState(List.of(new ClickableAnimal(1, original.x, original.y, true)),
                    List.of(), List.of("Playa Conchal"), "Playa Conchal");
            controller.mouseClicked(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 0, 0,
                    anchor.x, anchor.y, 1, false, MouseEvent.BUTTON1));
            Point destination = new Point(anchor.x + 40, anchor.y + 10);
            Point2D.Double target = panel.toMapPoint(destination).orElseThrow();
            controller.mouseClicked(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 0, 0,
                    destination.x, destination.y, 1, false, MouseEvent.BUTTON3));
            assertEquals(1, sent.size());
            var command = JsonParser.parseString(sent.getFirst()).getAsJsonObject();
            assertEquals("MOVE_ANIMALS", command.get("type").getAsString());
            assertEquals((int) Math.floor(target.x), command.getAsJsonObject("payload").get("targetX").getAsInt());
            assertEquals((int) Math.floor(target.y), command.getAsJsonObject("payload").get("targetY").getAsInt());
            wheel(panel, anchor, -100);
            assertEquals(10 / (baseScale * UIConstants.MAP_MAX_ZOOM),
                    panel.toMapPoint(adjacent).orElseThrow().x - panel.toMapPoint(anchor).orElseThrow().x, 1e-8);
            wheel(panel, anchor, 100);
            assertEquals(original.x, panel.toMapPoint(anchor).orElseThrow().x, 1e-8);
            assertEquals(original.y, panel.toMapPoint(anchor).orElseThrow().y, 1e-8);
            wheel(panel, anchor, -2);
            panel.clearGame();
            assertEquals(original.x, panel.toMapPoint(anchor).orElseThrow().x, 1e-8);
        });
    }

    @ParameterizedTest
    @EnumSource(AnimalSpecies.class)
    void everySpeciesRotatesInFourDirectionsAndKeepsHeadingWhenStopped(AnimalSpecies species) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE, false);
            panel.setSize(2400, 1792);
            BufferedImage sprite = CACHE.getAnimation(species.spriteName()).orElseThrow().idleImage();
            double facing = switch (species) {
                case TORTUGA -> 0;
                case RANA, MARIPOSA -> -Math.PI / 2;
                default -> Math.PI / 2;
            };
            for (int[] direction : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                panel.clearGame();
                panel.showSnapshot(state(species.name(), 400 - direction[0], 400 - direction[1]));
                Snapshot stopped = state(species.name(), 400, 400);
                panel.showSnapshot(stopped);
                panel.showSnapshot(stopped);
                BufferedImage actual = paint(panel);
                BufferedImage expected = new BufferedImage(2400, 1792, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = expected.createGraphics();
                graphics.drawImage(CACHE.getImage("Mapa.svg").orElseThrow(), 0, 0, null);
                AffineTransform placement = new AffineTransform();
                placement.translate(400, 400);
                placement.rotate(Math.atan2(direction[1], direction[0]) - facing);
                double scale = UIConstants.TEST_ANIMAL_SIZE / (double) Math.max(sprite.getWidth(), sprite.getHeight());
                placement.scale(scale, scale);
                placement.translate(-sprite.getWidth() / 2.0, -sprite.getHeight() / 2.0);
                graphics.drawImage(sprite, placement, null);
                graphics.dispose();
                int radius = UIConstants.TEST_ANIMAL_SIZE / 2;
                for (int y = 400 - radius; y <= 400 + radius; y++) {
                    for (int x = 400 - radius; x <= 400 + radius; x++) {
                        assertEquals(expected.getRGB(x, y), actual.getRGB(x, y),
                                species + " direccion " + direction[0] + "," + direction[1]);
                    }
                }
            }
        });
    }
}
