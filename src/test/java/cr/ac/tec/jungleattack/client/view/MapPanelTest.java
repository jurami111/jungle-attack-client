package cr.ac.tec.jungleattack.client.view;

import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.geom.Line2D;
import java.awt.geom.AffineTransform;
import cr.ac.tec.jungleattack.client.config.UIConstants;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class MapPanelTest {
    private static final SpriteCache CACHE = new SpriteCache();

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void reversingRepositionsIdleMonkeyAtOtherEndpointAndResetsMovement(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            JButton button = (JButton) panel.getComponent(0);
            JComboBox<?> routes = (JComboBox<?>) panel.getComponent(1);
            JComboBox<?> direction = (JComboBox<?>) panel.getComponent(3);
            routes.setSelectedIndex(1);
            try {
                button.doClick(0);
                panel.removeNotify();
                direction.setSelectedIndex(1);
                BufferedImage expected = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D original = expected.createGraphics();
                double scale = Math.min(width / 2400.0, height / 1792.0);
                double offsetX = (width - 2400 * scale) / 2;
                double offsetY = (height - 1792 * scale) / 2;
                original.translate(offsetX, offsetY);
                original.scale(scale, scale);
                original.drawImage(CACHE.getImage("Mapa.svg").orElseThrow(), 0, 0, null);
                BufferedImage animal = CACHE.getAnimation("mono").orElseThrow().idleImage();
                double animalScale = UIConstants.TEST_ANIMAL_SIZE
                        / (double) Math.max(animal.getWidth(), animal.getHeight());
                AffineTransform placement = new AffineTransform();
                placement.translate(900 - animal.getWidth() * animalScale / 2,
                        322 - animal.getHeight() * animalScale / 2);
                placement.scale(animalScale, animalScale);
                original.drawImage(animal, placement, null);
                original.dispose();
                BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = painted.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                // La mitad derecha del lienzo no contiene el resaltado que llega por la izquierda.
                for (int y = (int) (offsetY + 270 * scale); y < offsetY + 370 * scale; y++) {
                    for (int x = (int) Math.ceil(offsetX + 915 * scale);
                            x < offsetX + 955 * scale; x++) {
                        assertEquals(expected.getRGB(x, y), painted.getRGB(x, y));
                    }
                }
            } finally {
                panel.removeNotify();
            }
        });
    }

    @Test
    void bothDirectionsCanStartForEveryRouteAndDirectionIsLockedWhileMoving() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            JButton button = (JButton) panel.getComponent(0);
            JComboBox<?> routes = (JComboBox<?>) panel.getComponent(1);
            JLabel status = (JLabel) panel.getComponent(2);
            JComboBox<?> direction = (JComboBox<?>) panel.getComponent(3);
            assertEquals("Ida", direction.getSelectedItem());
            try {
                for (int i = 0; i < routes.getItemCount(); i++) {
                    routes.setSelectedIndex(i);
                    for (int d = 0; d < 2; d++) {
                        direction.setSelectedIndex(d);
                        boolean hidden = d == 0 ? i == 0 || i == 3 : i == 2;
                        assertEquals(hidden ? "Mono dentro de Cerro Zurqui" : "Listo para iniciar",
                                status.getText());
                        button.doClick(0);
                        assertFalse(button.isEnabled());
                        assertFalse(routes.isEnabled());
                        assertFalse(direction.isEnabled());
                        assertEquals(hidden ? "Dentro del tunel..." : "En movimiento", status.getText());
                        panel.removeNotify();
                        assertTrue(button.isEnabled());
                        assertTrue(routes.isEnabled());
                        assertTrue(direction.isEnabled());
                    }
                }
            } finally {
                panel.removeNotify();
            }
        });
    }

    @Test
    void selectsUpperRoutesAndLocksSelectionDuringMovement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            JButton button = (JButton) panel.getComponent(0);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            JLabel status = (JLabel) panel.getComponent(2);
            assertEquals(15, selector.getItemCount());
            try {
                for (int i = 0; i < 4; i++) {
                    selector.setSelectedIndex(i);
                    assertEquals(i == 0 || i == 3 ? "Mono dentro de Cerro Zurqui" : "Listo para iniciar",
                            status.getText());
                    button.doClick(0);
                    assertFalse(button.isEnabled());
                    assertFalse(selector.isEnabled());
                    assertEquals(i == 0 || i == 3 ? "Dentro del tunel..." : "En movimiento", status.getText());
                    panel.removeNotify();
                    assertTrue(button.isEnabled());
                    assertTrue(selector.isEnabled());
                }
            } finally {
                panel.removeNotify();
            }
        });
    }

    @Test
    void routesShareAnchorsAndStayWithinMapBounds() throws Exception {
        try (var stream = MapPanel.class.getResourceAsStream("/map/connections.json");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var definitions = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("connections");
            assertEquals(14, definitions.size());
            var anchors = new java.util.HashMap<String, com.google.gson.JsonElement>();
            for (var definition : definitions) {
                var route = definition.getAsJsonObject();
                var points = route.getAsJsonArray("points");
                assertTrue(points.size() >= 2);
                String from = route.get("fromZoneId").getAsString();
                String to = route.get("toZoneId").getAsString();
                var start = points.get(0);
                var end = points.get(points.size() - 1);
                assertEquals(start, anchors.computeIfAbsent(from, id -> start), from);
                assertEquals(end, anchors.computeIfAbsent(to, id -> end), to);
                for (var point : points) {
                    double x = point.getAsJsonObject().get("x").getAsDouble();
                    double y = point.getAsJsonObject().get("y").getAsDouble();
                    assertTrue(x >= 0 && x < 2400);
                    assertTrue(y >= 0 && y < 1792);
                }
                for (var hidden : route.getAsJsonArray("hiddenSegments")) {
                    assertTrue(hidden.getAsInt() >= 1 && hidden.getAsInt() < points.size());
                }
            }
            for (int i : new int[]{1, 2, 4, 5}) {
                var previous = definitions.get(i).getAsJsonObject();
                var next = definitions.get(i + 1).getAsJsonObject();
                assertEquals(previous.get("toZoneId"), next.get("fromZoneId"));
                var points = previous.getAsJsonArray("points");
                assertEquals(points.get(points.size() - 1), next.getAsJsonArray("points").get(0));
            }
            for (int[] pair : new int[][]{{4, 7}, {5, 8}, {6, 9}}) {
                var central = definitions.get(pair[0]).getAsJsonObject();
                var downward = definitions.get(pair[1]).getAsJsonObject();
                assertEquals(central.get("fromZoneId"), downward.get("fromZoneId"));
                assertEquals(central.getAsJsonArray("points").get(0),
                        downward.getAsJsonArray("points").get(0));
                assertTrue(downward.getAsJsonArray("hiddenSegments").isEmpty());
            }
            for (int[] endpoints : new int[][]{
                    {3, 10, 0}, {6, 10, 1}, {7, 11, 0}, {8, 11, 1},
                    {8, 12, 0}, {9, 12, 1}, {6, 13, 0}}) {
                var source = definitions.get(endpoints[0]).getAsJsonObject();
                var target = definitions.get(endpoints[1]).getAsJsonObject();
                var sourcePoints = source.getAsJsonArray("points");
                var targetPoints = target.getAsJsonArray("points");
                boolean atStart = endpoints[2] == 0;
                assertEquals(source.get("toZoneId"),
                        target.get(atStart ? "fromZoneId" : "toZoneId"));
                assertEquals(sourcePoints.get(sourcePoints.size() - 1),
                        targetPoints.get(atStart ? 0 : targetPoints.size() - 1));
            }
        }
    }

    @Test
    void zonesCoverMapWithoutOverlapAndContainTheirRouteAnchors() throws Exception {
        var zones = new java.util.HashMap<String, java.awt.geom.Rectangle2D.Double>();
        try (var stream = MapPanel.class.getResourceAsStream("/map/zones.json");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var definitions = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("zones");
            assertEquals(12, definitions.size());
            double area = 0;
            for (var definition : definitions) {
                var zone = definition.getAsJsonObject();
                var bounds = new java.awt.geom.Rectangle2D.Double(zone.get("x").getAsDouble(),
                        zone.get("y").getAsDouble(), zone.get("width").getAsDouble(),
                        zone.get("height").getAsDouble());
                assertTrue(new java.awt.geom.Rectangle2D.Double(0, 0, 2400, 1792).contains(bounds));
                for (var other : zones.values()) {
                    assertFalse(other.intersects(bounds), "Las zonas no deben encimarse");
                }
                assertNull(zones.put(zone.get("id").getAsString(), bounds));
                area += bounds.width * bounds.height;
            }
            assertEquals(2400.0 * 1792.0, area, 0.000001);
        }
        try (var stream = MapPanel.class.getResourceAsStream("/map/connections.json");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var definitions = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("connections");
            for (var definition : definitions) {
                var route = definition.getAsJsonObject();
                var points = route.getAsJsonArray("points");
                for (int end : new int[]{0, points.size() - 1}) {
                    var point = points.get(end).getAsJsonObject();
                    String zoneId = route.get(end == 0 ? "fromZoneId" : "toZoneId").getAsString();
                    assertTrue(zones.get(zoneId).contains(point.get("x").getAsDouble(),
                            point.get("y").getAsDouble()), zoneId + " en " + route.get("label"));
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void fogShowsOnlySelectedZoneWithDarkenedEdge(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            JComboBox<?> zones = (JComboBox<?>) panel.getComponent(4);
            double scale = Math.min(width / 2400.0, height / 1792.0);
            double offsetX = (width - 2400 * scale) / 2;
            double offsetY = (height - 1792 * scale) / 2;
            BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D original = baseline.createGraphics();
            original.translate(offsetX, offsetY);
            original.scale(scale, scale);
            original.drawImage(CACHE.getImage("Mapa.svg").orElseThrow(), 0, 0, null);
            original.dispose();
            assertEquals(13, zones.getItemCount());
            assertEquals("Sin niebla (todo el mapa)", zones.getSelectedItem());
            int centerX = (int) (offsetX + 900 * scale);
            int centerY = (int) (offsetY + 450 * scale);
            int otherX = (int) (offsetX + 2100 * scale);
            int otherY = (int) (offsetY + 1500 * scale);
            int edgeX = (int) Math.ceil(offsetX + 603 * scale);
            int edgeY = (int) (offsetY + 300 * scale);
            BufferedImage clear = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D clearGraphics = clear.createGraphics();
            panel.paint(clearGraphics);
            clearGraphics.dispose();
            assertEquals(baseline.getRGB(otherX, otherY), clear.getRGB(otherX, otherY));
            zones.setSelectedItem("Monteverde");
            BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = painted.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            assertEquals(baseline.getRGB(centerX, centerY), painted.getRGB(centerX, centerY));
            assertEquals(0xFF000000, painted.getRGB(otherX, otherY));
            int edge = painted.getRGB(edgeX, edgeY);
            assertTrue(((edge >> 16) & 255) < 40 && ((edge >> 8) & 255) < 40 && (edge & 255) < 40,
                    "El borde interior de la zona debe verse oscuro");
            panel.removeNotify();
        });
    }

    @Test
    void visibleRoutesCanBeSelectedAndStarted() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            JButton button = (JButton) panel.getComponent(0);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            JLabel status = (JLabel) panel.getComponent(2);
            String[] labels = {"Bahia Ballena - Cerro Chirripo",
                    "Cerro Chirripo - Volcan Poas", "Volcan Poas - Volcan Arenal",
                    "Bahia Ballena - Manuel Antonio", "Cerro Chirripo - Tortuguero",
                    "Volcan Poas - Tamarindo", "Braulio Carrillo - Volcan Arenal",
                    "Manuel Antonio - Tortuguero", "Tortuguero - Tamarindo",
                    "Volcan Arenal - Cerro de la Muerte",
                    "Monteverde - Zurqui - Poas (continuo)"};
            try {
                for (int i = 0; i < labels.length; i++) {
                    selector.setSelectedIndex(i + 4);
                    assertEquals(labels[i], selector.getSelectedItem());
                    assertEquals("Listo para iniciar", status.getText());
                    button.doClick(0);
                    assertFalse(button.isEnabled());
                    assertFalse(selector.isEnabled());
                    assertEquals("En movimiento", status.getText());
                    panel.removeNotify();
                    assertTrue(button.isEnabled());
                    assertTrue(selector.isEnabled());
                }
            } finally {
                panel.removeNotify();
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void remainingHighlightsAvoidVolcanoWaterAndPlateau(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            BufferedImage map = CACHE.getImage("Mapa.svg").orElseThrow();
            double scale = Math.min(width / 2400.0, height / 1792.0);
            double offsetX = (width - 2400 * scale) / 2;
            double offsetY = (height - 1792 * scale) / 2;
            BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D original = baseline.createGraphics();
            original.translate(offsetX, offsetY);
            original.scale(scale, scale);
            original.drawImage(map, 0, 0, null);
            original.dispose();
            int[][][] samples = {
                {{2110, 550}, {1870, 900}, {2240, 1060}},
                {{380, 1320}, {650, 1230}, {910, 1460}},
                {{910, 1460}, {1200, 1230}, {1510, 1350}},
                {{2110, 1180}, {1950, 1230}, {1840, 1450}}
            };
            for (int i = 0; i < samples.length; i++) {
                selector.setSelectedIndex(i + 10);
                BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = painted.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                for (int[] sample : samples[i]) {
                    int x = (int) (offsetX + sample[0] * scale);
                    int y = (int) (offsetY + sample[1] * scale);
                    assertNotEquals(baseline.getRGB(x, y), painted.getRGB(x, y));
                    assertTrue((painted.getRGB(x, y) & 255) > 140);
                }
                for (int[] obstacle : new int[][]{
                        {2110, 850}, {2110, 1050}, {2110, 1450},
                        {560, 1480}, {780, 1430}, {1020, 1450}}) {
                    int x = (int) (offsetX + obstacle[0] * scale);
                    int y = (int) (offsetY + obstacle[1] * scale);
                    assertEquals(baseline.getRGB(x, y), painted.getRGB(x, y),
                            "Volcano, sign, water and plateau must remain unchanged");
                }
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void downwardHighlightsFollowAccessesAndAvoidChannels(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            BufferedImage map = CACHE.getImage("Mapa.svg").orElseThrow();
            double scale = Math.min(width / 2400.0, height / 1792.0);
            double offsetX = (width - 2400 * scale) / 2;
            double offsetY = (height - 1792 * scale) / 2;
            BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D original = baseline.createGraphics();
            original.translate(offsetX, offsetY);
            original.scale(scale, scale);
            original.drawImage(map, 0, 0, null);
            original.dispose();
            int[][][] samples = {
                {{380, 1320}, {370, 1430}},
                {{910, 1320}, {910, 1375}, {910, 1460}},
                {{1510, 1320}, {1510, 1400}}
            };
            for (int i = 0; i < samples.length; i++) {
                selector.setSelectedIndex(i + 7);
                BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = painted.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                for (int[] sample : samples[i]) {
                    int x = (int) (offsetX + sample[0] * scale);
                    int y = (int) (offsetY + sample[1] * scale);
                    assertNotEquals(baseline.getRGB(x, y), painted.getRGB(x, y));
                    assertTrue((painted.getRGB(x, y) & 255) > 140);
                }
                for (int[] channel : new int[][]{{780, 1430}, {1020, 1450}, {1050, 1340}}) {
                    int x = (int) (offsetX + channel[0] * scale);
                    int y = (int) (offsetY + channel[1] * scale);
                    assertEquals(baseline.getRGB(x, y), painted.getRGB(x, y),
                            "Highlights must not cross the channels");
                }
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void centralHighlightsFollowSouthernRoadAndAvoidCrater(int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            BufferedImage map = CACHE.getImage("Mapa.svg").orElseThrow();
            double scale = Math.min(width / 2400.0, height / 1792.0);
            double offsetX = (width - 2400 * scale) / 2;
            double offsetY = (height - 1792 * scale) / 2;
            BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D original = baseline.createGraphics();
            original.translate(offsetX, offsetY);
            original.scale(scale, scale);
            original.drawImage(map, 0, 0, null);
            original.dispose();
            int[] roadSamples = {650, 1200, 1800};
            for (int i = 0; i < roadSamples.length; i++) {
                selector.setSelectedIndex(i + 4);
                BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = painted.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                int x = (int) (offsetX + roadSamples[i] * scale);
                int y = (int) (offsetY + 1230 * scale);
                assertNotEquals(baseline.getRGB(x, y), painted.getRGB(x, y));
                assertTrue((painted.getRGB(x, y) & 255) > 140);
                for (int[] landmark : new int[][]{{910, 800}, {1510, 900}, {2110, 850}}) {
                    x = (int) (offsetX + landmark[0] * scale);
                    y = (int) (offsetY + landmark[1] * scale);
                    assertEquals(baseline.getRGB(x, y), painted.getRGB(x, y),
                            "Mountains and craters must remain unchanged");
                }
            }
        });
    }

    @Test
    void doesNotPaintTunnelHighlightOrHiddenIdleMonkeyOnMountain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(1200, 896);
            JComboBox<?> selector = (JComboBox<?>) panel.getComponent(1);
            BufferedImage map = CACHE.getImage("Mapa.svg").orElseThrow();
            BufferedImage baseline = new BufferedImage(1200, 896, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = baseline.createGraphics();
            g.scale(0.5, 0.5);
            g.drawImage(map, 0, 0, null);
            g.dispose();
            for (int route : new int[]{0, 2, 3}) {
                selector.setSelectedIndex(route);
                BufferedImage painted = new BufferedImage(1200, 896, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = painted.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                for (int y = 140; y <= 170; y++) {
                    for (int x = 700; x <= 790; x++) {
                        assertEquals(baseline.getRGB(x, y), painted.getRGB(x, y),
                                "Mountain must remain free of hidden animal and tunnel highlight");
                    }
                }
            }
        });
    }

    @Test
    void buttonStartsMovementAndRemovingPanelStopsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            JButton button = (JButton) panel.getComponent(0);
            assertEquals("Probar movimiento", button.getText());
            assertTrue(button.isEnabled());
            try {
                button.doClick(0);
                assertFalse(button.isEnabled());
                panel.removeNotify();
                assertTrue(button.isEnabled());
                button.doClick(0);
                assertFalse(button.isEnabled());
            } finally {
                panel.removeNotify();
            }
            assertTrue(button.isEnabled());
        });
    }

    @ParameterizedTest
    @CsvSource({"1200,896", "1000,700", "600,900"})
    void keepsPathAlignedWithCenteredMap(int width, int height) throws Exception {
        BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage map = CACHE.getImage("Mapa.svg").orElseThrow();
        double scale = Math.min(width / (double) map.getWidth(), height / (double) map.getHeight());
        double x = (width - map.getWidth() * scale) / 2;
        double y = (height - map.getHeight() * scale) / 2;
        Graphics2D original = baseline.createGraphics();
        original.translate(x, y);
        original.scale(scale, scale);
        original.drawImage(map, 0, 0, null);
        original.setColor(UIConstants.TEST_PATH_COLOR);
        original.setStroke(new BasicStroke(UIConstants.TEST_PATH_WIDTH,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        original.draw(new Line2D.Double(1508, 364, 1508, 668));
        original.dispose();
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel(CACHE);
            panel.setSize(width, height);
            Graphics2D graphics = painted.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
        });
        int pathX = (int) (x + 1508 * scale);
        int pathY = (int) (y + 500 * scale);
        int pixel = painted.getRGB(pathX, pathY);
        assertEquals(baseline.getRGB(pathX, pathY), pixel);
        assertTrue((pixel & 255) > 140, "Expected cyan highlight on the path");
        int craterX = (int) (x + 1508 * scale);
        int craterY = (int) (y + 900 * scale);
        assertEquals(baseline.getRGB(craterX, craterY), painted.getRGB(craterX, craterY),
                "The highlight must not extend into the crater");
        int backgroundX = (int) (x + 100 * scale);
        int backgroundY = (int) (y + 100 * scale);
        assertEquals(baseline.getRGB(backgroundX, backgroundY), painted.getRGB(backgroundX, backgroundY));
        for (int[] sample : new int[][]{{1240, 860}, {1350, 1100}, {1460, 1148}}) {
            int px = (int) (x + sample[0] * scale);
            int py = (int) (y + sample[1] * scale);
            assertNotEquals(baseline.getRGB(px, py), painted.getRGB(px, py),
                    "Expected highlight along the exterior detour");
        }
        for (int[] obstacle : new int[][]{{1300, 900}, {1510, 1050}, {1700, 900}}) {
            int px = (int) (x + obstacle[0] * scale);
            int py = (int) (y + obstacle[1] * scale);
            assertEquals(baseline.getRGB(px, py), painted.getRGB(px, py),
                    "Crater rim and sign must remain unchanged");
        }
        int left = (int) (x + (1508 - UIConstants.TEST_ANIMAL_SIZE / 2.0) * scale);
        int top = (int) (y + (308 - UIConstants.TEST_ANIMAL_SIZE / 2.0) * scale);
        int right = (int) Math.ceil(x + (1508 + UIConstants.TEST_ANIMAL_SIZE / 2.0) * scale);
        int bottom = (int) Math.ceil(y + (308 + UIConstants.TEST_ANIMAL_SIZE / 2.0) * scale);
        for (int py = top; py <= bottom; py++) {
            for (int px = left; px <= right; px++) {
                assertEquals(baseline.getRGB(px, py), painted.getRGB(px, py),
                        "Hidden idle animal and tunnel must not be painted after resizing");
            }
        }
    }
}
