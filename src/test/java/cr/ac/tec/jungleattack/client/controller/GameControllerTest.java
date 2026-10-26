package cr.ac.tec.jungleattack.client.controller;

import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.google.gson.JsonParser;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.model.ClickableAnimal;
import cr.ac.tec.jungleattack.client.view.MapPanel;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    private static final SpriteCache CACHE = new SpriteCache();
    private static final List<ClickableAnimal> OWN = List.of(
            new ClickableAnimal(1, 300, 300, true),
            new ClickableAnimal(2, 320, 300, true),
            new ClickableAnimal(3, 400, 450, false));
    private static final List<ClickableAnimal> OTHERS = List.of(
            new ClickableAnimal(21, 450, 400, true),
            new ClickableAnimal(22, 900, 300, true));
    private static final List<String> ZONES = List.of("Playa Conchal", "Monteverde");

    @ParameterizedTest
    @CsvSource({"1200,896,500,500", "1000,700,501,499", "600,900,500,500"})
    void selectsMultipleAnimalsAndBuildsExactCommandsAtDifferentScales(
            int width, int height, int expectedX, int expectedY) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(width, height);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 320, 300, MouseEvent.BUTTON1, true);
            assertTrue(messages.isEmpty(), "Seleccionar no envia ninguna orden");
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            var expectedMove = JsonParser.parseString("""
                    {"type":"MOVE_ANIMALS","payload":{"animalIds":[1,2],"targetX":500,"targetY":500}}
                    """).getAsJsonObject();
            expectedMove.getAsJsonObject("payload").addProperty("targetX", expectedX);
            expectedMove.getAsJsonObject("payload").addProperty("targetY", expectedY);
            assertEquals(expectedMove, JsonParser.parseString(messages.getLast()));
            var payload = JsonParser.parseString(messages.getLast()).getAsJsonObject()
                    .getAsJsonObject("payload");
            assertEquals(expectedX, payload.get("targetX").getAsBigDecimal().intValueExact());
            assertEquals(expectedY, payload.get("targetY").getAsBigDecimal().intValueExact());
            assertFalse(payload.get("targetX").toString().contains("."));
            assertFalse(payload.get("targetY").toString().contains("."));
            click(view, 450, 400, MouseEvent.BUTTON3, false);
            assertJson("""
                    {"type":"ATTACK","payload":{"animalIds":[1,2],"targetId":21}}
                    """, messages.getLast());
            int previousCount = messages.size();
            zones(view).setSelectedItem("Monteverde");
            assertEquals(previousCount + 1, messages.size());
            assertJson("""
                    {"type":"VIEW_ZONE","payload":{"zone":"Monteverde"}}
                    """, messages.getLast());
            assertEquals("Playa Conchal", zones(view).getSelectedItem(),
                    "Solicitar una zona no cambia la vista hasta recibir confirmacion");
            view.removeNotify();
        });
    }

    @Test
    void manualZoneChoiceCancelsAutomaticFollowing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE, false);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, List.of(), ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 900, 300, MouseEvent.BUTTON3, false);
            JComboBox<?> selector = java.util.Arrays.stream(view.getComponents())
                    .filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .filter(java.awt.Component::isVisible).findFirst().orElseThrow();
            selector.setSelectedItem("Monteverde");
            assertEquals(2, messages.size());
            controller.updateState(List.of(new ClickableAnimal(1, 910, 316, true)),
                    List.of(), ZONES, "Playa Conchal");
            assertEquals(2, messages.size(), "La eleccion manual no dispara seguimiento automatico");
            controller.updateState(List.of(new ClickableAnimal(1, 910, 316, true)),
                    List.of(), ZONES, "Monteverde");
            assertEquals(2, messages.size());
            view.removeNotify();
        });
    }

    @Test
    void followsAnySelectedExplorerAcrossNewZonesWithoutSwitchingBackToTheGroup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE, false);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            List<ClickableAnimal> own = List.of(
                    new ClickableAnimal(1, 311, 327, true),
                    new ClickableAnimal(2, 321, 327, true),
                    new ClickableAnimal(3, 910, 316, true));
            List<String> occupiedZones = List.of("Playa Conchal", "Monteverde");
            controller.updateState(own, List.of(), occupiedZones, "Playa Conchal");
            click(view, 311, 327, MouseEvent.BUTTON1, false);
            click(view, 321, 327, MouseEvent.BUTTON1, true);
            click(view, 700, 327, MouseEvent.BUTTON3, false);
            assertEquals("MOVE_ANIMALS", JsonParser.parseString(messages.getFirst())
                    .getAsJsonObject().get("type").getAsString());

            controller.updateState(List.of(
                    new ClickableAnimal(1, 311, 327, true),
                    new ClickableAnimal(2, 910, 316, true),
                    new ClickableAnimal(3, 910, 316, true)), List.of(), occupiedZones, "Playa Conchal");
            assertJson("""
                    {"type":"VIEW_ZONE","payload":{"zone":"Monteverde"}}
                    """, messages.getLast());

            controller.updateState(List.of(
                    new ClickableAnimal(1, 311, 327, true),
                    new ClickableAnimal(2, 910, 316, true),
                    new ClickableAnimal(3, 910, 316, true)), List.of(), occupiedZones, "Monteverde");
            int confirmedCount = messages.size();
            controller.updateState(List.of(
                    new ClickableAnimal(1, 311, 327, true),
                    new ClickableAnimal(2, 910, 316, true),
                    new ClickableAnimal(3, 910, 316, true)), List.of(), occupiedZones, "Monteverde");
            assertEquals(confirmedCount, messages.size(), "No debe volver a la zona del grupo rezagado");

            List<String> threeZones = List.of("Playa Conchal", "Monteverde", "Cerro Zurqui");
            controller.updateState(List.of(
                    new ClickableAnimal(1, 311, 327, true),
                    new ClickableAnimal(2, 1507, 309, true)), List.of(), threeZones, "Monteverde");
            assertJson("""
                    {"type":"VIEW_ZONE","payload":{"zone":"Cerro Zurqui"}}
                    """, messages.getLast());
            view.removeNotify();
        });
    }

    @Test
    void retriesAutomaticZoneRequestAfterServerRejectsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE, false);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(List.of(new ClickableAnimal(1, 311, 327, true)),
                    List.of(), List.of("Playa Conchal"), "Playa Conchal");
            click(view, 311, 327, MouseEvent.BUTTON1, false);
            click(view, 700, 327, MouseEvent.BUTTON3, false);
            controller.updateState(List.of(new ClickableAnimal(1, 910, 316, true)),
                    List.of(), List.of("Playa Conchal", "Monteverde"), "Playa Conchal");
            assertEquals(2, messages.size());

            controller.onAutomaticZoneRequestRejected();
            controller.updateState(List.of(new ClickableAnimal(1, 910, 316, true)),
                    List.of(), List.of("Playa Conchal", "Monteverde"), "Playa Conchal");
            assertEquals(3, messages.size());
            assertJson("""
                    {"type":"VIEW_ZONE","payload":{"zone":"Monteverde"}}
                    """, messages.getLast());
            view.removeNotify();
        });
    }

    @Test
    void controlClickTogglesAndPlainClickReplacesOrClearsSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 320, 300, MouseEvent.BUTTON1, true);
            click(view, 300, 300, MouseEvent.BUTTON1, true);
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertJson("""
                    {"type":"MOVE_ANIMALS","payload":{"animalIds":[2],"targetX":500,"targetY":500}}
                    """, messages.getLast());
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertJson("""
                    {"type":"MOVE_ANIMALS","payload":{"animalIds":[1],"targetX":500,"targetY":500}}
                    """, messages.getLast());
            click(view, 550, 550, MouseEvent.BUTTON1, true);
            assertEquals("Animales seleccionados: 1", status(view).getText());
            click(view, 550, 550, MouseEvent.BUTTON1, false);
            int count = messages.size();
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertEquals(count, messages.size());
            assertTrue(status(view).getText().startsWith("Selecciona primero"));
            view.removeNotify();
        });
    }

    @Test
    void rejectsHiddenSelectionButAllowsMovementFiftyPixelsIntoFogWithoutAttackingHiddenAnimals() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(600, 900);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 400, 450, MouseEvent.BUTTON1, false);
            assertEquals("Animales seleccionados: 0", status(view).getText());
            click(view, 450, 400, MouseEvent.BUTTON1, false);
            assertEquals("Animales seleccionados: 0", status(view).getText());
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 320, 300, MouseEvent.BUTTON3, false);
            assertTrue(messages.isEmpty());
            click(view, 672, 316, MouseEvent.BUTTON1, false);
            assertEquals("Selecciona animales dentro de la zona visible", status(view).getText());
            click(view, 672, 316, MouseEvent.BUTTON3, false);
            assertEquals(1, messages.size());
            var command = JsonParser.parseString(messages.getFirst()).getAsJsonObject();
            assertEquals("MOVE_ANIMALS", command.get("type").getAsString(),
                    "Un enemigo oculto no se convierte en objetivo de ataque");
            assertEquals(JsonParser.parseString("""
                    {"animalIds":[1],"targetX":672,"targetY":316}
                    """), command.getAsJsonObject("payload"));
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_CLICKED, 0, 0,
                    100, 10, 1, false, MouseEvent.BUTTON3));
            assertEquals(1, messages.size());
            assertTrue(view.toMapPoint(new java.awt.Point(100, 10)).isEmpty(),
                    "La franja fuera del mapa no tiene coordenadas del juego");
            view.removeNotify();
        });
    }

    @Test
    void stateUpdatesPruneSelectionAndMoveItsHighlightWithoutSendingCommands() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            List<ClickableAnimal> moved = List.of(new ClickableAnimal(1, 400, 400, true));
            controller.updateState(moved, OTHERS, ZONES, "Playa Conchal");
            BufferedImage image = new BufferedImage(1200, 896, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            view.paint(graphics);
            graphics.dispose();
            assertEquals(UIConstants.ANIMAL_SELECTION_COLOR.getRGB(),
                    image.getRGB((int) ((400 + UIConstants.ANIMAL_SELECTION_RADIUS) / 2), 200));
            controller.updateState(List.of(new ClickableAnimal(1, 400, 400, false)),
                    OTHERS, ZONES, "Playa Conchal");
            assertEquals("Animales seleccionados: 0", status(view).getText());
            controller.updateState(moved, OTHERS, ZONES, "Playa Conchal");
            click(view, 400, 400, MouseEvent.BUTTON1, false);
            controller.updateState(moved, OTHERS, ZONES, "Monteverde");
            assertEquals("Animales seleccionados: 0", status(view).getText());
            assertEquals("Monteverde", zones(view).getSelectedItem());
            assertTrue(messages.isEmpty(), "Actualizar la vista no debe reenviar VIEW_ZONE");
            view.removeNotify();
        });
    }

    @Test
    void missingStateAndMissingSenderAreExplicitAndDoNotSend() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            assertEquals("Esperando los datos de animales del servidor", status(view).getText());
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertEquals("No hay envio conectado: la orden no fue enviada", status(view).getText());
            controller.updateState(List.of(new ClickableAnimal(1, Double.NaN, 300, true)),
                    OTHERS, ZONES, "Playa Conchal");
            assertEquals("Estado invalido para los clics: revisar animales y zonas", status(view).getText());
            controller.updateState(OWN, OTHERS, List.of("Zona inexistente"), "Zona inexistente");
            assertEquals("Estado invalido para los clics: revisar animales y zonas", status(view).getText());
            view.removeNotify();
        });
    }

    @Test
    void nearestTargetWinsEvenWhenOwnAnimalIsAlsoNearCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(List.of(new ClickableAnimal(1, 430, 400, true)),
                    OTHERS, ZONES, "Playa Conchal");
            click(view, 430, 400, MouseEvent.BUTTON1, false);
            click(view, 450, 400, MouseEvent.BUTTON3, false);
            assertJson("""
                    {"type":"ATTACK","payload":{"animalIds":[1],"targetId":21}}
                    """, messages.getLast());
            view.removeNotify();
        });
    }

    @Test
    void animalsUseSixteenMapUnitsAsClickRadius() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            controller.updateState(List.of(new ClickableAnimal(1, 300, 300, true)),
                    List.of(), ZONES, "Playa Conchal");
            assertEquals(32, UIConstants.TEST_ANIMAL_SIZE);
            assertEquals(16.0, UIConstants.ANIMAL_CLICK_RADIUS);
            assertEquals(12.0, UIConstants.ANIMAL_SELECTION_RADIUS);
            click(view, 316, 300, MouseEvent.BUTTON1, false);
            assertEquals("Animales seleccionados: 1", status(view).getText());
            click(view, 318, 300, MouseEvent.BUTTON1, false);
            assertEquals("Animales seleccionados: 0", status(view).getText());
            view.removeNotify();
        });
    }

    @Test
    void emptyZonesClearSelectionHideMapAndAllowLaterStateRecovery() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            view.setSize(1200, 896);
            GameController controller = new GameController(view);
            List<String> messages = new ArrayList<>();
            controller.setMessageSender(messages::add);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            controller.updateState(OWN, List.of(), List.of(), null);
            assertEquals(0, zones(view).getItemCount());
            assertNull(zones(view).getSelectedItem());
            assertFalse(view.isVisiblePoint(300, 300));
            assertEquals("No hay una zona visible", status(view).getText());
            BufferedImage image = new BufferedImage(1200, 896, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            view.paint(graphics);
            graphics.dispose();
            for (int y = 100; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    assertEquals(UIConstants.FOG_COLOR.getRGB(), image.getRGB(x, y));
                }
            }
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertEquals("No hay una zona visible", status(view).getText());
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            assertEquals("Animales seleccionados: 0", status(view).getText());
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertTrue(messages.isEmpty(), "La seleccion anterior no debe recuperarse");
            click(view, 300, 300, MouseEvent.BUTTON1, false);
            click(view, 500, 500, MouseEvent.BUTTON3, false);
            assertEquals(1, messages.size());
            view.removeNotify();
        });
    }

    @Test
    void rejectsInconsistentEmptyAndSelectedZones() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel view = new MapPanel(CACHE);
            GameController controller = new GameController(view);
            controller.updateState(OWN, OTHERS, ZONES, "Playa Conchal");
            controller.updateState(OWN, List.of(), List.of(), "Playa Conchal");
            assertEquals("Estado invalido para los clics: revisar animales y zonas", status(view).getText());
            assertEquals("Playa Conchal", zones(view).getSelectedItem());
            controller.updateState(OWN, OTHERS, ZONES, null);
            assertEquals("Estado invalido para los clics: revisar animales y zonas", status(view).getText());
            assertEquals("Playa Conchal", zones(view).getSelectedItem());
            view.removeNotify();
        });
    }

    private static void click(MapPanel view, double x, double y, int button, boolean control) {
        double scale = Math.min(view.getWidth() / 2400.0, view.getHeight() / 1792.0);
        int screenX = (int) Math.round((view.getWidth() - 2400 * scale) / 2 + x * scale);
        int screenY = (int) Math.round((view.getHeight() - 1792 * scale) / 2 + y * scale);
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_CLICKED, 0,
                control ? InputEvent.CTRL_DOWN_MASK : 0, screenX, screenY, 1, false, button));
    }

    private static JComboBox<?> zones(MapPanel view) {
        return (JComboBox<?>) view.getComponent(4);
    }

    private static JLabel status(MapPanel view) {
        return (JLabel) view.getComponent(2);
    }

    private static void assertJson(String expected, String actual) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
    }
}
