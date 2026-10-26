package cr.ac.tec.jungleattack.client.view;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import cr.ac.tec.jungleattack.client.model.AnimalSpecies;
import cr.ac.tec.jungleattack.client.model.GameState;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.Snapshot;
import cr.ac.tec.jungleattack.client.network.ServerMessageParser;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

import static org.junit.jupiter.api.Assertions.*;

class ClientPanelTest {
    private static final SpriteCache CACHE = new SpriteCache();
    private static final Map<String, String> EXAMPLES = examples();

    private static Map<String, String> examples() {
        var stream = ClientPanelTest.class.getResourceAsStream("/protocol/server-messages.jsonl");
        assertNotNull(stream);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Map<String, String> lines = new LinkedHashMap<>();
            reader.lines().forEach(line -> lines.put(
                    JsonParser.parseString(line).getAsJsonObject().get("type").getAsString(), line));
            return lines;
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static JsonObject message(String type) {
        return JsonParser.parseString(EXAMPLES.get(type)).getAsJsonObject();
    }

    private static List<Component> components(Container container) {
        List<Component> all = new ArrayList<>();
        for (Component component : container.getComponents()) {
            all.add(component);
            if (component instanceof Container child) {
                all.addAll(components(child));
            }
        }
        return all;
    }

    private static JButton button(ClientPanel panel, String name) {
        return components(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(value -> value.getText().equals(name)).findFirst().orElseThrow();
    }

    private static List<JTextField> fields(ClientPanel panel) {
        return components(panel).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast).toList();
    }

    private static MapPanel map(ClientPanel panel) {
        return components(panel).stream().filter(MapPanel.class::isInstance).map(MapPanel.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void fullFlowSendsExactCommandsAndUsesServerAcknowledgementsWithoutSockets() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(CACHE);
            List<String> sent = new ArrayList<>();
            assertFalse(button(panel, "Crear partida").isEnabled());
            panel.setMessageSender(sent::add);
            button(panel, "Crear partida").doClick(0);
            assertTrue(sent.isEmpty());
            fields(panel).get(0).setText(" Ana ");
            button(panel, "Crear partida").doClick(0);
            assertEquals(JsonParser.parseString(
                    "{\"type\":\"CREATE_MATCH\",\"payload\":{\"nickname\":\"Ana\"}}"),
                    JsonParser.parseString(sent.getFirst()));
            assertFalse(button(panel, "Crear partida").isEnabled());
            panel.receiveServerLine(EXAMPLES.get("MATCH_CREATED"));
            assertEquals(GameState.Phase.WAITING, panel.getModel().phase());
            panel.receiveServerLine(EXAMPLES.get("PLAYER_JOINED"));
            assertEquals("Luis", panel.getModel().opponent());
            JsonObject selection = message("ANIMAL_SELECTION");
            selection.getAsJsonObject("payload").add("yourSpecies", JsonNull.INSTANCE);
            panel.receiveServerLine(selection.toString());
            assertTrue(button(panel, "Confirmar animal").isEnabled());
            button(panel, "Confirmar animal").doClick(0);
            assertEquals(JsonParser.parseString(
                    "{\"type\":\"SELECT_ANIMAL\",\"payload\":{\"species\":\"MARIPOSA\"}}"),
                    JsonParser.parseString(sent.get(1)));
            assertFalse(button(panel, "Confirmar animal").isEnabled());
            panel.receiveServerLine(EXAMPLES.get("ANIMAL_SELECTION"));
            assertFalse(button(panel, "Confirmar animal").isEnabled());
            panel.receiveServerLine(EXAMPLES.get("GAME_STARTED"));
            assertEquals(GameState.Phase.PLAYING, panel.getModel().phase());
            MapPanel map = map(panel);
            map.setSize(2400, 1792);
            assertTrue(map.isVisiblePoint(311, 327));
            assertFalse(map.isVisiblePoint(910, 316));
            assertTrue(java.util.Arrays.stream(map.getComponents())
                    .filter(JButton.class::isInstance).noneMatch(Component::isVisible));
            assertEquals(1, components(map).stream().filter(JComboBox.class::isInstance)
                    .filter(Component::isVisible).count());
            click(panel, 311, 327, MouseEvent.BUTTON1);
            click(panel, 400, 420, MouseEvent.BUTTON3);
            JsonObject move = JsonParser.parseString(sent.get(2)).getAsJsonObject();
            assertEquals("MOVE_ANIMALS", move.get("type").getAsString());
            assertEquals(400, move.getAsJsonObject("payload").get("targetX").getAsInt());
            assertEquals(420, move.getAsJsonObject("payload").get("targetY").getAsInt());
            assertEquals("[1]", move.getAsJsonObject("payload").get("animalIds").toString());
            panel.receiveServerLine(EXAMPLES.get("STATE_UPDATE"));
            assertEquals(1, panel.getModel().snapshot().visibleAnimals().size());
            click(panel, 450, 400, MouseEvent.BUTTON3);
            JsonObject attack = JsonParser.parseString(sent.get(3)).getAsJsonObject();
            assertEquals("ATTACK", attack.get("type").getAsString());
            assertEquals(21, attack.getAsJsonObject("payload").get("targetId").getAsInt());
            panel.receiveServerLine(EXAMPLES.get("GAME_STARTED"));
            assertTrue(panel.getModel().snapshot().visibleAnimals().isEmpty());
            panel.receiveServerLine(EXAMPLES.get("GAME_OVER"));
            assertEquals(GameState.Phase.FINISHED, panel.getModel().phase());
            JTable table = components(panel).stream().filter(JTable.class::isInstance)
                    .map(JTable.class::cast).findFirst().orElseThrow();
            assertEquals(7, table.getRowCount());
            int count = sent.size();
            click(panel, 400, 420, MouseEvent.BUTTON3);
            assertEquals(count, sent.size());
            button(panel, "Volver al inicio").doClick(0);
            assertEquals(GameState.Phase.HOME, panel.getModel().phase());
            assertNull(panel.getModel().snapshot());
            fields(panel).get(1).setText("a7k2");
            button(panel, "Unirse").doClick(0);
            assertEquals(JsonParser.parseString(
                    "{\"type\":\"JOIN_MATCH\",\"payload\":{\"nickname\":\"Ana\",\"code\":\"A7K2\"}}"),
                    JsonParser.parseString(sent.getLast()));
            panel.receiveServerLine(EXAMPLES.get("MATCH_JOINED"));
            assertEquals(GameState.Phase.WAITING, panel.getModel().phase());
            panel.receiveServerLine(EXAMPLES.get("MATCH_CANCELLED"));
            assertEquals(GameState.Phase.FINISHED, panel.getModel().phase());
            assertEquals(0, table.getRowCount());
            panel.connectionClosed("EOF");
            assertEquals(GameState.Phase.DISCONNECTED, panel.getModel().phase());
            assertFalse(button(panel, "Crear partida").isEnabled());
            assertNull(panel.getModel().snapshot());
        });
    }

    private static void click(ClientPanel panel, int x, int y, int button) {
        panel.getController().mouseClicked(new MouseEvent(map(panel), MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, x, y, 1, false, button));
    }

    @Test
    void explorationFollowsConfirmedAnimalAcrossZonesWithoutRevealingFogEarly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(CACHE);
            List<String> sent = new ArrayList<>();
            panel.setMessageSender(sent::add);
            MapPanel map = map(panel);
            map.setSize(2400, 1792);
            panel.receiveServerLine(EXAMPLES.get("GAME_STARTED"));
            click(panel, 311, 327, MouseEvent.BUTTON1);
            click(panel, 910, 316, MouseEvent.BUTTON3);
            assertEquals("MOVE_ANIMALS", JsonParser.parseString(sent.getFirst())
                    .getAsJsonObject().get("type").getAsString());
            assertFalse(map.isVisiblePoint(910, 316));
            assertEquals(java.awt.Color.BLACK.getRGB(), paint(map).getRGB(910, 316));

            JsonObject update = message("GAME_STARTED");
            JsonObject payload = update.getAsJsonObject("payload");
            JsonObject monteverde = JsonParser.parseString(
                    "{\"name\":\"Monteverde\",\"centerX\":910,\"centerY\":316}").getAsJsonObject();
            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("x", 910);
            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("y", 316);
            payload.getAsJsonArray("visibleZones").add(monteverde);
            panel.receiveServerLine(update.toString());
            assertEquals(2, sent.size());
            assertEquals(JsonParser.parseString(
                    "{\"type\":\"VIEW_ZONE\",\"payload\":{\"zone\":\"Monteverde\"}}"),
                    JsonParser.parseString(sent.getLast()));
            assertFalse(map.isVisiblePoint(910, 316),
                    "Pedir la zona no revela datos del snapshot anterior");
            panel.receiveServerLine(update.toString());
            assertEquals(2, sent.size(), "No reenviar VIEW_ZONE en cada tick");
            payload.add("selectedZone", monteverde);
            payload.add("visibleFlags", new com.google.gson.JsonArray());
            payload.add("visibleCoffeeCups", new com.google.gson.JsonArray());
            panel.receiveServerLine(update.toString());
            assertTrue(map.isVisiblePoint(910, 316));
            assertFalse(map.isVisiblePoint(311, 327));
            assertNotEquals(java.awt.Color.BLACK.getRGB(), paint(map).getRGB(910, 316));
            click(panel, 1000, 400, MouseEvent.BUTTON3);
            assertEquals("[1]", JsonParser.parseString(sent.getLast()).getAsJsonObject()
                    .getAsJsonObject("payload").get("animalIds").toString(),
                    "El explorador conserva su seleccion al cruzar");

            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("alive", false);
            panel.receiveServerLine(update.toString());
            int count = sent.size();
            click(panel, 1000, 400, MouseEvent.BUTTON3);
            assertEquals(count, sent.size(), "Un explorador muerto ya no recibe ordenes");
            map.removeNotify();
        });
    }

    @Test
    void retriesAutomaticZoneSwitchAfterServerError() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(CACHE);
            List<String> sent = new ArrayList<>();
            panel.setMessageSender(sent::add);
            MapPanel map = map(panel);
            map.setSize(2400, 1792);
            JsonObject update = message("GAME_STARTED");
            panel.receiveServerLine(update.toString());
            click(panel, 311, 327, MouseEvent.BUTTON1);
            click(panel, 700, 327, MouseEvent.BUTTON3);

            JsonObject payload = update.getAsJsonObject("payload");
            JsonObject monteverde = JsonParser.parseString(
                    "{\"name\":\"Monteverde\",\"centerX\":910,\"centerY\":316}").getAsJsonObject();
            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("x", 910);
            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("y", 316);
            payload.getAsJsonArray("visibleZones").add(monteverde);
            panel.receiveServerLine(update.toString());
            assertEquals(2, sent.size());

            panel.receiveServerLine(EXAMPLES.get("ERROR"));
            panel.receiveServerLine(update.toString());
            assertEquals(3, sent.size());
            assertEquals("VIEW_ZONE", JsonParser.parseString(sent.getLast()).getAsJsonObject()
                    .get("type").getAsString());
            map.removeNotify();
        });
    }

    @Test
    void tenAnimalsAndZoneChangesWaitForAcknowledgementAndNoZoneClearsSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(CACHE);
            List<String> sent = new ArrayList<>();
            panel.setMessageSender(sent::add);
            MapPanel map = map(panel);
            map.setSize(2400, 1792);
            JsonObject message = message("GAME_STARTED");
            JsonObject payload = message.getAsJsonObject("payload");
            var own = payload.getAsJsonArray("ownAnimals");
            for (int id = 3; id <= 10; id++) {
                JsonObject animal = own.get(0).getAsJsonObject().deepCopy();
                animal.addProperty("id", id);
                animal.addProperty("x", id == 10 ? 910 : 340 + id * 20);
                animal.addProperty("y", id == 10 ? 316 : 450);
                own.add(animal);
            }
            JsonObject monteverde = JsonParser.parseString(
                    "{\"name\":\"Monteverde\",\"centerX\":910,\"centerY\":316}").getAsJsonObject();
            payload.getAsJsonArray("visibleZones").add(monteverde);
            panel.receiveServerLine(message.toString());
            assertEquals(10, panel.getModel().snapshot().ownAnimals().size());
            JComboBox<?> zones = java.util.Arrays.stream(map.getComponents())
                    .filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .filter(Component::isVisible).findFirst().orElseThrow();
            assertEquals(2, zones.getItemCount());
            click(panel, 311, 327, MouseEvent.BUTTON1);
            zones.setSelectedItem("Monteverde");
            assertEquals(JsonParser.parseString(
                    "{\"type\":\"VIEW_ZONE\",\"payload\":{\"zone\":\"Monteverde\"}}"),
                    JsonParser.parseString(sent.getFirst()));
            assertEquals("Playa Conchal", zones.getSelectedItem());
            assertFalse(map.isVisiblePoint(910, 316));
            payload.add("selectedZone", monteverde);
            panel.receiveServerLine(message.toString());
            assertEquals("Monteverde", zones.getSelectedItem());
            assertTrue(map.isVisiblePoint(910, 316));
            assertFalse(map.isVisiblePoint(311, 327));
            click(panel, 950, 350, MouseEvent.BUTTON3);
            assertEquals(1, sent.size());
            click(panel, 910, 316, MouseEvent.BUTTON1);
            click(panel, 950, 350, MouseEvent.BUTTON3);
            JsonObject move = JsonParser.parseString(sent.getLast()).getAsJsonObject();
            assertEquals("[10]", move.getAsJsonObject("payload").get("animalIds").toString());
            payload.add("visibleZones", new com.google.gson.JsonArray());
            payload.add("selectedZone", JsonNull.INSTANCE);
            payload.add("visibleCoffeeCups", new com.google.gson.JsonArray());
            payload.add("visibleFlags", new com.google.gson.JsonArray());
            panel.receiveServerLine(message.toString());
            assertEquals(0, zones.getItemCount());
            assertFalse(map.isVisiblePoint(910, 316));
            int count = sent.size();
            click(panel, 950, 350, MouseEvent.BUTTON3);
            assertEquals(count, sent.size());
            payload.getAsJsonArray("visibleZones").add(monteverde);
            payload.add("selectedZone", monteverde);
            panel.receiveServerLine(message.toString());
            click(panel, 950, 350, MouseEvent.BUTTON3);
            assertEquals(count, sent.size());
            click(panel, 910, 316, MouseEvent.BUTTON1);
            payload.addProperty("running", false);
            panel.receiveServerLine(message.toString());
            click(panel, 950, 350, MouseEvent.BUTTON3);
            assertEquals(count, sent.size());
        });
    }

    @Test
    void errorsAreVisibleAndInvalidMessagesDoNotReplaceTheSnapshot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(CACHE);
            List<String> sent = new ArrayList<>();
            panel.setMessageSender(sent::add);
            fields(panel).get(0).setText("Ana");
            fields(panel).get(1).setText("?");
            button(panel, "Unirse").doClick(0);
            assertTrue(sent.isEmpty());
            panel.receiveServerLine(EXAMPLES.get("GAME_STARTED"));
            Snapshot initial = panel.getModel().snapshot();
            panel.receiveServerLine("{");
            assertSame(initial, panel.getModel().snapshot());
            assertTrue(panel.getModel().status().contains("JSON"));
            JsonObject unknown = message("STATE_UPDATE");
            unknown.getAsJsonObject("payload").getAsJsonObject("ownPack").addProperty("species", "DESCONOCIDO");
            panel.receiveServerLine(unknown.toString());
            assertSame(initial, panel.getModel().snapshot());
            assertTrue(panel.getModel().status().contains("incompatible"));
            unknown = message("STATE_UPDATE");
            unknown.getAsJsonObject("payload").getAsJsonArray("visibleFlags").get(0)
                    .getAsJsonObject().addProperty("color", "rojo");
            panel.receiveServerLine(unknown.toString());
            assertSame(initial, panel.getModel().snapshot());
            panel.receiveServerLine(EXAMPLES.get("ERROR"));
            assertEquals(GameState.Phase.PLAYING, panel.getModel().phase());
            assertTrue(panel.getModel().status().contains("Servidor:"));
            panel.reportConnectionError("No se pudo enviar");
            assertTrue(panel.getModel().status().contains("No se pudo enviar"));
            panel.connectionClosed("Cerrada");
            panel.receiveServerLine(EXAMPLES.get("STATE_UPDATE"));
            assertEquals(GameState.Phase.DISCONNECTED, panel.getModel().phase());
            assertNull(panel.getModel().snapshot());
        });
    }

    @Test
    void networkCallbacksCanRunOutsideSwingAndNotifyOnTheEventThread() throws Exception {
        ClientPanel[] holder = new ClientPanel[1];
        AtomicBoolean onSwing = new AtomicBoolean(true);
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new ClientPanel(CACHE);
            holder[0].getModel().addObserver(state ->
                    onSwing.set(onSwing.get() && SwingUtilities.isEventDispatchThread()));
        });
        holder[0].setMessageSender(json -> fail("No command should be sent by receiving messages"));
        holder[0].receiveServerLine(EXAMPLES.get("GAME_STARTED"));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(onSwing.get());
            assertEquals(GameState.Phase.PLAYING, holder[0].getModel().phase());
        });
        holder[0].connectionClosed("EOF");
        SwingUtilities.invokeAndWait(() -> assertEquals(GameState.Phase.DISCONNECTED, holder[0].getModel().phase()));
    }

    private static Snapshot snapshot(JsonObject message) {
        var parsed = new ServerMessageParser().parse(message.toString());
        assertTrue(parsed.error().isEmpty(), () -> parsed.error().orElse(""));
        return (Snapshot) parsed.message().orElseThrow().payload();
    }

    private static BufferedImage paint(MapPanel map) {
        BufferedImage image = new BufferedImage(2400, 1792, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        map.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static boolean differs(BufferedImage first, BufferedImage second, int x, int y) {
        for (int py = y - 22; py <= y + 22; py++) {
            for (int px = x - 22; px <= x + 22; px++) {
                if (first.getRGB(px, py) != second.getRGB(px, py)) {
                    return true;
                }
            }
        }
        return false;
    }

    @ParameterizedTest
    @EnumSource(AnimalSpecies.class)
    void productionMapDrawsEachRealSpeciesAndRemovesDeadAnimals(AnimalSpecies species) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(CACHE.getAnimation(species.spriteName()).isPresent());
            MapPanel map = new MapPanel(CACHE, false);
            map.setSize(2400, 1792);
            JsonObject message = message("GAME_STARTED");
            JsonObject payload = message.getAsJsonObject("payload");
            payload.add("visibleCoffeeCups", new com.google.gson.JsonArray());
            payload.add("visibleFlags", new com.google.gson.JsonArray());
            JsonObject animal = payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject();
            animal.addProperty("x", 400);
            animal.addProperty("y", 400);
            animal.addProperty("species", species.name());
            JsonObject second = payload.getAsJsonArray("ownAnimals").get(1).getAsJsonObject();
            second.addProperty("alive", false);
            map.showSnapshot(snapshot(message));
            BufferedImage alive = paint(map);
            animal.addProperty("alive", false);
            map.showSnapshot(snapshot(message));
            BufferedImage dead = paint(map);
            assertTrue(differs(alive, dead, 400, 400), species.name());
            assertEquals(java.awt.Color.BLACK.getRGB(), dead.getRGB(910, 316));
            map.clearGame();
            assertEquals(java.awt.Color.BLACK.getRGB(), paint(map).getRGB(400, 400));
            map.removeNotify();
        });
    }

    @Test
    void rendersFlagsAndCoffeeAndUsesActualEnergyThenClearsInvisibleState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel map = new MapPanel(CACHE, false);
            map.setSize(2400, 1792);
            JsonObject message = message("GAME_STARTED");
            JsonObject payload = message.getAsJsonObject("payload");
            map.showSnapshot(snapshot(message));
            BufferedImage initial = paint(map);
            payload.add("visibleCoffeeCups", new com.google.gson.JsonArray());
            payload.add("visibleFlags", new com.google.gson.JsonArray());
            payload.getAsJsonArray("ownAnimals").get(0).getAsJsonObject().addProperty("energy", 0);
            map.showSnapshot(snapshot(message));
            BufferedImage removed = paint(map);
            assertTrue(differs(initial, removed, 450, 400));
            assertNotEquals(initial.getRGB(345, 280), removed.getRGB(345, 280));
            int energyY = 327 - cr.ac.tec.jungleattack.client.config.UIConstants.TEST_ANIMAL_SIZE / 2
                    - cr.ac.tec.jungleattack.client.config.UIConstants.ENERGY_BAR_GAP
                    - cr.ac.tec.jungleattack.client.config.UIConstants.ENERGY_BAR_HEIGHT + 2;
            assertNotEquals(initial.getRGB(300, energyY), removed.getRGB(300, energyY));
            payload.add("visibleZones", new com.google.gson.JsonArray());
            payload.add("selectedZone", JsonNull.INSTANCE);
            map.showSnapshot(snapshot(message));
            assertFalse(map.isVisiblePoint(311, 327));
            assertEquals(java.awt.Color.BLACK.getRGB(), paint(map).getRGB(311, 327));
            map.removeNotify();
        });
    }
}
