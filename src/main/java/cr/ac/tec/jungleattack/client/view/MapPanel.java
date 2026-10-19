package cr.ac.tec.jungleattack.client.view; // panel que muestra el mapa del juego

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.controller.Animator;
import cr.ac.tec.jungleattack.client.model.Connection;
import cr.ac.tec.jungleattack.client.view.utils.FogOfWar;
import cr.ac.tec.jungleattack.client.view.utils.SpriteAnimation;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;// caché de sprites para el panel del mapa

public class MapPanel extends JPanel {

    // direccion hacia la que mira el mono en sus imagenes originales (hacia abajo)
    private static final double MONO_FACING = Animator.DOWN;
    private static final String NO_FOG_LABEL = "Sin niebla (todo el mapa)";

    private final Optional<BufferedImage> mapImage;
    private final List<Route> routes;
    private final List<Route> reversedRoutes;
    private final JComboBox<String> routeSelector;
    private final JComboBox<String> directionSelector;
    private final List<ZoneArea> zones;
    private final JComboBox<String> zoneSelector;
    private final JLabel movementStatus;
    private final Optional<BufferedImage> idleAnimalImage;
    private final Optional<SpriteAnimation> animalAnimation;
    private final JButton movementButton;
    private final Animator animator;
    private Optional<Animator.State> animalState = Optional.empty();

    public MapPanel(SpriteCache spriteCache) { // constructor del panel del mapa
        mapImage = spriteCache.getImage("Mapa.svg");
        List<Route> loaded = new ArrayList<>(loadConnections());
        Optional<Connection> entry = loaded.stream().map(Route::connection)
                .filter(connection -> connection.fromZoneId().equals("MONTEVERDE")
                        && connection.toZoneId().equals("CERRO_ZURQUI")).findFirst();
        Optional<Connection> exit = loaded.stream().map(Route::connection)
                .filter(connection -> connection.fromZoneId().equals("CERRO_ZURQUI")
                        && connection.toZoneId().equals("VOLCAN_POAS")).findFirst();
        if (entry.isPresent() && exit.isPresent()) {
            entry.get().followedBy(exit.get()).ifPresent(connection -> loaded.add(new Route(
                    "Monteverde - Zurqui - Poas (continuo)", connection, createPath(connection))));
        } else {
            System.err.println("No se pudo preparar la prueba continua: faltan conexiones de Zurqui");
        }
        routes = List.copyOf(loaded);
        reversedRoutes = routes.stream().map(route -> {
            Connection reverse = route.connection().reversed();
            return new Route(route.label(), reverse, createPath(reverse));
        }).toList();
        routeSelector = new JComboBox<>(routes.stream().map(Route::label).toArray(String[]::new));
        directionSelector = new JComboBox<>(new String[]{"Ida", "Vuelta"});
        directionSelector.setToolTipText("Ida: primera zona a segunda; vuelta: segunda zona a primera");
        zones = loadZones();
        List<String> zoneLabels = new ArrayList<>();
        zoneLabels.add(NO_FOG_LABEL);
        zones.forEach(zone -> zoneLabels.add(zone.name()));
        zoneSelector = new JComboBox<>(zoneLabels.toArray(String[]::new));
        zoneSelector.setToolTipText("Zona visible: el resto del mapa queda cubierto por la niebla");
        zoneSelector.addActionListener(event -> repaint());
        movementStatus = new JLabel(" ");
        movementStatus.setOpaque(true);
        movementStatus.setBackground(Color.WHITE);
        movementStatus.setForeground(Color.BLACK);
        animalAnimation = spriteCache.getAnimation("mono");
        idleAnimalImage = animalAnimation.map(animation -> animation.idleImage());
        if (idleAnimalImage.isEmpty()) {
            System.err.println("No se pudo obtener la imagen de reposo del mono desde la cache");
        }
        setBackground(new Color(40, 90, 45));
        setOpaque(true);
        setLayout(new FlowLayout(FlowLayout.LEADING));
        movementButton = new JButton("Probar movimiento");
        boolean available = mapImage.isPresent() && !routes.isEmpty() && animalAnimation.isPresent();
        movementButton.setEnabled(available);
        routeSelector.setEnabled(available);
        directionSelector.setEnabled(available);
        animator = new Animator(state -> {
            animalState = Optional.of(state);
            movementButton.setEnabled(available && !state.moving());
            routeSelector.setEnabled(available && !state.moving());
            directionSelector.setEnabled(available && !state.moving());
            movementStatus.setText(state.visible()
                    ? (state.moving() ? "En movimiento" : "Recorrido terminado")
                    : (state.moving() ? "Dentro del tunel..." : "Mono dentro de Cerro Zurqui"));
            repaint();
        });
        movementButton.addActionListener(event -> { // maneja el evento de clic en el botón de movimiento
            if (selectedRoute().isPresent() && animalAnimation.isPresent()) {
                animator.startAtSpeed(selectedRoute().get().connection(), animalAnimation.get(),
                        UIConstants.TEST_MOVEMENT_SPEED);
            }
        });
        routeSelector.addActionListener(event -> {
            animalState = Optional.empty();
            updateIdleStatus();
            repaint();
        });
        directionSelector.addActionListener(event -> {
            animalState = Optional.empty();
            updateIdleStatus();
            repaint();
        });
        add(movementButton);
        add(routeSelector);
        add(movementStatus);
        add(directionSelector);
        add(zoneSelector);
        updateIdleStatus();
    }

    // la primera opcion del selector muestra todo el mapa; las demas corresponden a cada zona
    private Optional<ZoneArea> selectedZone() {
        int index = zoneSelector.getSelectedIndex();
        return index >= 1 ? Optional.of(zones.get(index - 1)) : Optional.empty();
    }

    private Optional<Route> selectedRoute() {
        int index = routeSelector.getSelectedIndex();
        return index >= 0 ? Optional.of(
                (directionSelector.getSelectedIndex() == 1 ? reversedRoutes : routes).get(index))
                : Optional.empty();
    }

    private void updateIdleStatus() {
        movementStatus.setText(selectedRoute().map(route -> route.connection().hiddenSegments().contains(1)
                ? "Mono dentro de Cerro Zurqui" : "Listo para iniciar").orElse("Sin conexiones disponibles"));
    }

    @Override// se asegura de que el método removeNotify se sobrescriba correctamente
    public void removeNotify() {
        animator.stop();
        super.removeNotify();
    }

    @Override// se asegura de que el método paintComponent se sobrescriba correctamente
    protected void paintComponent(Graphics graphics) { // dibuja el contenido del panel del mapa
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create(); // crea una copia del objeto Graphics para dibujar el contenido del panel

        try {// intenta dibujar la imagen del mapa, manejando el caso en que no se pueda cargar
            if (mapImage.isEmpty()) {
                graphics2D.setColor(Color.WHITE);
                graphics2D.drawString("No se pudo cargar Mapa.svg", 20, 30);
                return;
            }

            BufferedImage image = mapImage.get(); // obtiene la imagen del mapa del Optional

            double scale = Math.min(// calcula la escala para ajustar la imagen del mapa al tamaño del panel
                    getWidth() / (double) image.getWidth(),
                    getHeight() / (double) image.getHeight());

            double x = (getWidth() - image.getWidth() * scale) / 2;
            double y = (getHeight() - image.getHeight() * scale) / 2;
            
            // aplica la transformación de traslación y escala antes de dibujar la imagen del mapa
            graphics2D.translate(x, y);
            graphics2D.scale(scale, scale);
            graphics2D.drawImage(image, 0, 0, this);
            Optional<Route> selected = selectedRoute();
            if (selected.isPresent()) {
                graphics2D.setColor(UIConstants.TEST_PATH_COLOR);
                graphics2D.setStroke(new BasicStroke(UIConstants.TEST_PATH_WIDTH,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics2D.draw(selected.get().path());
            }
            boolean visible = animalState.map(Animator.State::visible)
                    .orElseGet(() -> selected.map(route -> !route.connection().hiddenSegments().contains(1))
                            .orElse(false));
            if (idleAnimalImage.isPresent() && selected.isPresent() && visible) { // dibuja el animal en su posición actual según la conexión de prueba y el estado de animación
                BufferedImage animal = animalState.map(Animator.State::image).orElse(idleAnimalImage.get());// obtiene la imagen actual del animal, usando la imagen inactiva si no hay animación en curso
                Point2D.Double start = selected.get().connection().pathPoints().get(0);
                double animalX = animalState.map(Animator.State::x).orElse(start.x);
                double animalY = animalState.map(Animator.State::y).orElse(start.y);
                double animalScale = UIConstants.TEST_ANIMAL_SIZE
                        / (double) Math.max(animal.getWidth(), animal.getHeight()); // calcula la escala del animal para que se ajuste al tamaño definido en las constantes de la interfaz de usuario
                double heading = animalState.map(Animator.State::heading).orElse(MONO_FACING);
                // se gira alrededor del centro: trasladar al punto, rotar, escalar y centrar la imagen
                AffineTransform placement = new AffineTransform();
                placement.translate(animalX, animalY);
                placement.rotate(heading - MONO_FACING);
                placement.scale(animalScale, animalScale);
                placement.translate(-animal.getWidth() / 2.0, -animal.getHeight() / 2.0);
                graphics2D.drawImage(animal, placement, this);
            }
            selectedZone().ifPresent(zone -> FogOfWar.paint(graphics2D,
                    new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()), zone.bounds()));
        } finally {
            graphics2D.dispose();
        }
    }

        private static Path2D.Double createPath(Connection connection) {// crea un Path2D a partir de los puntos de la conexión
            Path2D.Double path = new Path2D.Double();
            Point2D.Double first = connection.pathPoints().get(0);
            path.moveTo(first.x, first.y);// mueve el punto inicial del path a la primera coordenada de la conexión
            for (int i = 1; i < connection.pathPoints().size(); i++) {
                Point2D.Double point = connection.pathPoints().get(i);
                if (connection.hiddenSegments().contains(i)) {
                    path.moveTo(point.x, point.y);
                } else {
                    path.lineTo(point.x, point.y);
                }
            }
            return path;
        }

        private static List<Route> loadConnections() {
            var stream = MapPanel.class.getResourceAsStream("/map/connections.json");
            if (stream == null) {
                System.err.println("No se encontro /map/connections.json");
                return List.of();
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                MapDefinition map = new GsonBuilder().setStrictness(Strictness.STRICT)
                        .create().fromJson(reader, MapDefinition.class);
                if (map == null || map.connections() == null || map.connections().isEmpty()) {
                    System.err.println("El mapa requiere una lista de conexiones");
                    return List.of();
                }
                List<Route> loaded = new ArrayList<>();
                var labels = new HashSet<String>();
                for (int index = 0; index < map.connections().size(); index++) {
                    PathDefinition definition = map.connections().get(index);
                    if (definition == null || definition.label() == null || definition.label().isBlank()
                            || definition.fromZoneId() == null || definition.fromZoneId().isBlank()
                            || definition.toZoneId() == null || definition.toZoneId().isBlank()
                            || definition.fromZoneId().equals(definition.toZoneId())
                            || definition.points() == null || definition.points().size() < 2
                            || definition.points().stream().anyMatch(point -> point == null
                                || point.x() == null || point.y() == null
                                || !Double.isFinite(point.x()) || !Double.isFinite(point.y())
                                || point.x() < 0 || point.y() < 0)
                            || definition.hiddenSegments() == null
                            || definition.hiddenSegments().stream().anyMatch(segment -> segment == null
                                    || segment < 1 || segment >= definition.points().size())
                            || !labels.add(definition.label())) {
                        System.err.println("Conexion invalida en /map/connections.json, posicion " + (index + 1));
                        continue;
                    }
                    List<Point2D.Double> points = definition.points().stream()
                            .map(point -> new Point2D.Double(point.x(), point.y())).toList();
                    Connection connection = new Connection(definition.fromZoneId(), definition.toZoneId(),
                            points, definition.hiddenSegments());
                    loaded.add(new Route(definition.label(), connection, createPath(connection)));
                }
                return List.copyOf(loaded);
            } catch (IOException | JsonParseException exception) {
                System.err.println("No se pudieron leer las conexiones: " + exception.getMessage());
                return List.of();
            }
        }

        private static List<ZoneArea> loadZones() {
            var stream = MapPanel.class.getResourceAsStream("/map/zones.json");
            if (stream == null) {
                System.err.println("No se encontro /map/zones.json");
                return List.of();
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                ZoneMapDefinition map = new GsonBuilder().setStrictness(Strictness.STRICT)
                        .create().fromJson(reader, ZoneMapDefinition.class);
                if (map == null || map.zones() == null || map.zones().isEmpty()) {
                    System.err.println("El mapa requiere una lista de zonas");
                    return List.of();
                }
                List<ZoneArea> loaded = new ArrayList<>();
                var ids = new HashSet<String>();
                for (int index = 0; index < map.zones().size(); index++) {
                    ZoneDefinition zone = map.zones().get(index);
                    if (zone == null || zone.id() == null || zone.id().isBlank()
                            || zone.name() == null || zone.name().isBlank()
                            || zone.x() == null || zone.y() == null
                            || zone.width() == null || zone.height() == null
                            || !Double.isFinite(zone.x()) || !Double.isFinite(zone.y())
                            || !Double.isFinite(zone.width()) || !Double.isFinite(zone.height())
                            || zone.x() < 0 || zone.y() < 0 || zone.width() <= 0 || zone.height() <= 0
                            || !ids.add(zone.id())) {
                        System.err.println("Zona invalida en /map/zones.json, posicion " + (index + 1));
                        continue;
                    }
                    loaded.add(new ZoneArea(zone.id(), zone.name(),
                            new Rectangle2D.Double(zone.x(), zone.y(), zone.width(), zone.height())));
                }
                return List.copyOf(loaded);
            } catch (IOException | JsonParseException exception) {
                System.err.println("No se pudieron leer las zonas: " + exception.getMessage());
                return List.of();
            }
        }

        private record Route(String label, Connection connection, Path2D.Double path) {
        }

        private record ZoneArea(String id, String name, Rectangle2D.Double bounds) {
        }

        private record ZoneMapDefinition(List<ZoneDefinition> zones) {
        }

        private record ZoneDefinition(String id, String name, Double x, Double y, Double width, Double height) {
        }

        private record MapDefinition(List<PathDefinition> connections) {
        }

        private record PathDefinition(String label, String fromZoneId, String toZoneId,
                List<PathPoint> points, List<Integer> hiddenSegments) {
        }

        private record PathPoint(Double x, Double y) { // representa un punto en el camino de la conexión
        }
}