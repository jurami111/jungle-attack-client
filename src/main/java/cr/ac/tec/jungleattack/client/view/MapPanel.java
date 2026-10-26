package cr.ac.tec.jungleattack.client.view; // panel que muestra el mapa del juego

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.controller.Animator;
import cr.ac.tec.jungleattack.client.model.ClickableAnimal;
import cr.ac.tec.jungleattack.client.model.Connection;
import cr.ac.tec.jungleattack.client.model.MapZones;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.view.utils.EnergyBar;
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
    private int displayedAnimalEnergy = UIConstants.MAX_PLAYER_ENERGY;
    // lista de animales seleccionados actualmente en el panel del mapa
    private List<ClickableAnimal> selectedAnimals = List.of();
    private Consumer<String> zoneRequestHandler;
    private boolean updatingZones;
    private boolean gameZonesActive;
    private final boolean demonstration;
    private final SpriteCache spriteCache;
    private final Timer gameAnimationTimer;
    private ServerMessage.Snapshot snapshot;
    private Area visibleGameArea = new Area();
    private String visibleGameZone;
    private final Map<Integer, Double> headings = new HashMap<>();
    private final java.util.Set<Integer> movingIds = new HashSet<>();
    private long animationStart = System.nanoTime();
    private double zoom = 1.0;
    private double cameraOffsetX;
    private double cameraOffsetY;

    public MapPanel(SpriteCache spriteCache) { // constructor del panel del mapa
        this(spriteCache, true);
    }

    public MapPanel(SpriteCache spriteCache, boolean demonstration) {
        this.spriteCache = spriteCache;
        this.demonstration = demonstration;
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
        zoneSelector.addActionListener(event -> {
            if (!updatingZones && zoneRequestHandler != null
                    && zoneSelector.getSelectedItem() instanceof String zone) {
                zoneRequestHandler.accept(zone);
            }
            repaint();
        });
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
        gameAnimationTimer = new Timer(UIConstants.ANIMATION_DELAY_MS, event -> repaint());
        addMouseWheelListener(this::zoomAtCursor);
        if (!demonstration) {
            movementButton.setVisible(false);
            routeSelector.setVisible(false);
            directionSelector.setVisible(false);
            showGameZones(List.of(), null);
            movementStatus.setText("Esperando partida");
        }
    }

    public void showSnapshot(ServerMessage.Snapshot state) {
        Map<Integer, ServerMessage.Animal> previous = new HashMap<>();
        if (snapshot != null) {
            snapshot.ownAnimals().forEach(animal -> previous.put(animal.id(), animal));
            snapshot.visibleAnimals().forEach(animal -> previous.put(animal.id(), animal));
        }
        movingIds.clear();
        java.util.Set<Integer> currentIds = new HashSet<>();
        java.util.stream.Stream.concat(state.ownAnimals().stream(), state.visibleAnimals().stream())
                .forEach(animal -> {
                    currentIds.add(animal.id());
                    ServerMessage.Animal old = previous.get(animal.id());
                    if (old != null && (old.x() != animal.x() || old.y() != animal.y())) {
                        headings.put(animal.id(), Math.atan2(animal.y() - old.y(), animal.x() - old.x()));
                        movingIds.add(animal.id());
                    }
                });
        headings.keySet().retainAll(currentIds);
        snapshot = state;
        showGameZones(state.visibleZones().stream().map(ServerMessage.Zone::name).toList(),
                state.selectedZone() == null ? null : state.selectedZone().name());
        if (isDisplayable() && state.running()) {
            gameAnimationTimer.start();
        } else {
            gameAnimationTimer.stop();
        }
        repaint();
    }

    public void clearGame() {
        snapshot = null;
        headings.clear();
        movingIds.clear();
        animationStart = System.nanoTime();
        zoom = 1.0;
        cameraOffsetX = 0;
        cameraOffsetY = 0;
        gameAnimationTimer.stop();
        showSelection(List.of());
        showGameZones(List.of(), null);
    }

    public void stopGameAnimation() {
        gameAnimationTimer.stop();
        movingIds.clear();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!demonstration && snapshot != null && snapshot.running()) {
            gameAnimationTimer.start();
        }
    }

    // la primera opcion del selector muestra todo el mapa; las demas corresponden a cada zona
    private Optional<ZoneArea> selectedZone() {
        return zones.stream().filter(zone -> zone.name().equals(zoneSelector.getSelectedItem())).findFirst();
    }
    // establece el manejador de solicitudes de cambio de zona
    public void setZoneRequestHandler(Consumer<String> handler) {
        zoneRequestHandler = handler;
    }
    // verifica si existe una zona con el nombre dado
    public boolean hasZone(String name) {
        return demonstration ? zones.stream().anyMatch(zone -> zone.name().equals(name))
                : MapZones.ALL.stream().anyMatch(zone -> zone.name().equals(name));
    }
    // muestra las zonas del juego en el selector y selecciona la zona indicada
    public final void showGameZones(List<String> names, String selectedName) {
        gameZonesActive = true;
        updatingZones = true;
        try {
            boolean sameNames = zoneSelector.getItemCount() == names.size();
            // comprueba si los nombres de las zonas en el selector coinciden con los nombres proporcionados
            for (int i = 0; sameNames && i < names.size(); i++) {
                sameNames = names.get(i).equals(zoneSelector.getItemAt(i));
            }
            // si los nombres no coinciden, se actualiza el selector con los nuevos nombres
            if (!sameNames) {
                zoneSelector.removeAllItems();
                names.forEach(zoneSelector::addItem);
            }
            // selecciona la zona indicada en el selector
            zoneSelector.setSelectedItem(selectedName);
            if (!demonstration && mapImage.isPresent() && !java.util.Objects.equals(visibleGameZone, selectedName)) {
                visibleGameArea = MapZones.visibleArea(selectedName,
                        mapImage.get().getWidth(), mapImage.get().getHeight());
                visibleGameZone = selectedName;
            }
            // marca que se está actualizando el selector para evitar disparar eventos innecesarios
        } finally {
            updatingZones = false;
        }
        repaint();
    }

    public boolean isVisiblePoint(double x, double y) {
        if (!demonstration) {
            return MapZones.at(x, y)
                    .map(zone -> zone.name().equals(zoneSelector.getSelectedItem())).orElse(false);
        }
        return selectedZone().map(zone -> zone.bounds().contains(x, y)).orElse(false);
    }

    private double mapScale(BufferedImage image) {
        return zoom * Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
    }

    private double mapOffsetX(BufferedImage image, double scale) {
        return (getWidth() - image.getWidth() * scale) / 2 + cameraOffsetX * scale;
    }

    private double mapOffsetY(BufferedImage image, double scale) {
        return (getHeight() - image.getHeight() * scale) / 2 + cameraOffsetY * scale;
    }

    private void zoomAtCursor(MouseWheelEvent event) {
        if (mapImage.isEmpty() || toMapPoint(event.getPoint()).isEmpty()) {
            return;
        }
        BufferedImage image = mapImage.get();
        double previousScale = mapScale(image);
        double nextZoom = Math.max(1.0, Math.min(UIConstants.MAP_MAX_ZOOM,
                zoom * Math.pow(UIConstants.MAP_ZOOM_STEP, -event.getPreciseWheelRotation())));
        if (nextZoom == zoom) {
            return;
        }
        zoom = nextZoom;
        double nextScale = mapScale(image);
        // Conserva bajo el cursor la misma coordenada global al cambiar la escala.
        cameraOffsetX += (event.getX() - getWidth() / 2.0) * (1 / nextScale - 1 / previousScale);
        cameraOffsetY += (event.getY() - getHeight() / 2.0) * (1 / nextScale - 1 / previousScale);
        if (zoom == 1.0) {
            cameraOffsetX = 0;
            cameraOffsetY = 0;
        }
        event.consume();
        repaint();
    }
    // convierte un punto de la interfaz a un punto en el mapa, considerando la escala y el centrado de la imagen
    public Optional<Point2D.Double> toMapPoint(Point point) {
        if (mapImage.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return Optional.empty();
        }
        BufferedImage image = mapImage.get();
        double scale = mapScale(image);
        double x = (point.x - mapOffsetX(image, scale)) / scale;
        double y = (point.y - mapOffsetY(image, scale)) / scale;
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                ? Optional.of(new Point2D.Double(x, y)) : Optional.empty();
    }

    public void showSelection(List<ClickableAnimal> animals) {
        selectedAnimals = List.copyOf(animals);
        repaint();
    }

    public void showInteractionStatus(String message) {
        movementStatus.setText(message);
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
        gameAnimationTimer.stop();
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

            double scale = mapScale(image);

            double x = mapOffsetX(image, scale);
            double y = mapOffsetY(image, scale);
            
            // aplica la transformación de traslación y escala antes de dibujar la imagen del mapa
            graphics2D.translate(x, y);
            graphics2D.scale(scale, scale);
            if (!demonstration) {
                paintGame(graphics2D, image);
                return;
            }
            if (gameZonesActive && selectedZone().isEmpty()) {
                graphics2D.setColor(UIConstants.FOG_COLOR);
                graphics2D.fill(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()));
                return;
            }
            graphics2D.drawImage(image, 0, 0, this);
            Optional<Route> selected = selectedRoute();
            paintRoute(graphics2D, selected);
            paintAnimal(graphics2D, selected);
            paintSelection(graphics2D);
            selectedZone().ifPresent(zone -> FogOfWar.paint(graphics2D,
                    new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()), zone.bounds()));
        } finally {
            graphics2D.dispose();
        }
    }

    private void paintGame(Graphics2D graphics, BufferedImage image) {
        graphics.setColor(UIConstants.FOG_COLOR);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        if (snapshot == null || snapshot.selectedZone() == null) {
            return;
        }
        Graphics2D visible = (Graphics2D) graphics.create();
        try {
            visible.clip(visibleGameArea);
            visible.drawImage(image, 0, 0, this);
            for (ServerMessage.CoffeeCup cup : snapshot.visibleCoffeeCups()) {
                visible.setColor(Color.WHITE);
                visible.fillRoundRect(cup.x() - 8, cup.y() - 9, 16, 18, 4, 4);
                visible.setColor(new Color(85, 45, 20));
                visible.fillOval(cup.x() - 6, cup.y() - 8, 12, 6);
                visible.setStroke(new BasicStroke(2));
                visible.drawOval(cup.x() + 5, cup.y() - 5, 8, 10);
            }
            for (ServerMessage.Flag flag : snapshot.visibleFlags()) {
                visible.setColor(Color.DARK_GRAY);
                visible.setStroke(new BasicStroke(3));
                visible.drawLine(flag.x(), flag.y(), flag.x(), flag.y() - 55);
                visible.setColor(Color.decode(flag.color()));
                visible.fillRect(flag.x(), flag.y() - 55, 32, 20);
                if (flag.captured()) {
                    visible.setColor(Color.WHITE);
                    visible.drawString("Capturada", flag.x() + 4, flag.y() - 62);
                }
            }
            snapshot.ownAnimals().forEach(animal -> paintGameAnimal(visible, animal, true));
            snapshot.visibleAnimals().forEach(animal -> paintGameAnimal(visible, animal, false));
            paintSelection(visible);
        } finally {
            visible.dispose();
        }
    }

    private void paintGameAnimal(Graphics2D graphics, ServerMessage.Animal animal, boolean own) {
        if (!animal.alive() || !isVisiblePoint(animal.x(), animal.y())) {
            return;
        }
        Optional<SpriteAnimation> animation = spriteCache.getAnimation(animal.species().toLowerCase(Locale.ROOT));
        if (animation.isEmpty()) {
            graphics.setColor(Color.MAGENTA);
            graphics.fillOval(animal.x() - 16, animal.y() - 16, 32, 32);
            graphics.drawString("Sin sprite: " + animal.species(), animal.x(), animal.y() - 30);
            return;
        }
        SpriteAnimation sequence = animation.get();
        boolean moving = movingIds.contains(animal.id()) && snapshot.running();
        long elapsed = (System.nanoTime() - animationStart) / 1_000_000;
        BufferedImage frame = moving
                ? sequence.frames().get((int) (elapsed / sequence.frameDurationMs() % sequence.frames().size()))
                : sequence.idleImage();
        double scale = UIConstants.TEST_ANIMAL_SIZE / (double) Math.max(frame.getWidth(), frame.getHeight());
        AffineTransform placement = new AffineTransform();
        placement.translate(animal.x(), animal.y());
        double facing = originalFacing(animal.species());
        placement.rotate(headings.getOrDefault(animal.id(), facing) - facing);
        placement.scale(scale, scale);
        placement.translate(-frame.getWidth() / 2.0, -frame.getHeight() / 2.0);
        graphics.drawImage(frame, placement, this);
        graphics.setColor(own ? Color.YELLOW : Boolean.TRUE.equals(animal.bot()) ? Color.LIGHT_GRAY : Color.RED);
        graphics.drawString((own ? "Tu " : Boolean.TRUE.equals(animal.bot()) ? "Bot " : "Rival ")
                + animal.id(), animal.x() - 20, animal.y() + 36);
        EnergyBar.paint(graphics, animal.x(), animal.y() - UIConstants.TEST_ANIMAL_SIZE / 2.0
                - UIConstants.ENERGY_BAR_GAP - UIConstants.ENERGY_BAR_HEIGHT,
                animal.energy(), Boolean.TRUE.equals(animal.bot())
                        ? UIConstants.MAX_BOT_ENERGY : UIConstants.MAX_PLAYER_ENERGY);
    }

    private static double originalFacing(String species) {
        return switch (species) {
            case "TORTUGA" -> 0;
            case "RANA", "MARIPOSA" -> -Math.PI / 2;
            default -> MONO_FACING;
        };
    }

    private void paintRoute(Graphics2D graphics2D, Optional<Route> selected) {
        if (selected.isPresent()) {
            graphics2D.setColor(UIConstants.TEST_PATH_COLOR);
            graphics2D.setStroke(new BasicStroke(UIConstants.TEST_PATH_WIDTH,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics2D.draw(selected.get().path());
        }
    }

    private void paintAnimal(Graphics2D graphics2D, Optional<Route> selected) {
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
            EnergyBar.paint(graphics2D, animalX,
                    animalY - UIConstants.TEST_ANIMAL_SIZE / 2.0
                            - UIConstants.ENERGY_BAR_GAP - UIConstants.ENERGY_BAR_HEIGHT,
                    displayedAnimalEnergy, UIConstants.MAX_PLAYER_ENERGY);
        }
    }

    private void paintSelection(Graphics2D graphics2D) {
        graphics2D.setColor(UIConstants.ANIMAL_SELECTION_COLOR);
        graphics2D.setStroke(new BasicStroke(UIConstants.ANIMAL_SELECTION_WIDTH));
        for (ClickableAnimal animal : selectedAnimals) {
            double radius = UIConstants.ANIMAL_SELECTION_RADIUS;
            graphics2D.draw(new Ellipse2D.Double(animal.x() - radius, animal.y() - radius,
                    radius * 2, radius * 2));
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
