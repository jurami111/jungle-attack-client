package cr.ac.tec.jungleattack.client.controller;

// Controlador del juego que maneja la interacción del usuario con el mapa y los animales.
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet; // para verificar duplicados en las zonas visibles
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;//distinge los botenes del maus (izquierdo y derecho)

import com.google.gson.Gson;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.model.ClickableAnimal;
import cr.ac.tec.jungleattack.client.model.MapZones;
import cr.ac.tec.jungleattack.client.view.MapPanel;

// Controlador que gestiona la lógica de interacción del usuario con los animales y el mapa.
public final class GameController extends MouseAdapter {
    private final MapPanel view;
    private final Gson gson = new Gson();
    private final Set<Integer> selectedIds = new LinkedHashSet<>();
    private List<ClickableAnimal> ownAnimals = List.of();
    private List<ClickableAnimal> visibleAnimals = List.of();
    private List<String> visibleZones = List.of();
    private String selectedZone;
    private boolean stateReceived;
    private boolean interactionEnabled = true;
    private final Set<Integer> exploringIds = new LinkedHashSet<>();
    private String requestedFollowZone;
    private Consumer<String> messageSender;//funcion que se conecta con el servidor para enviar mensajes

    public GameController(MapPanel view) {
        this.view = view;
        view.addMouseListener(this);
        view.setZoneRequestHandler(this::requestZone);// conesta el selectior de las zonas con el metodo requestZone
    }

    public void setMessageSender(Consumer<String> messageSender) { // guarda la funcion que envia mensajes al servidor
        this.messageSender = messageSender;
    }

    public void setInteractionEnabled(boolean enabled) {
        interactionEnabled = enabled;
        if (!enabled) {
            selectedIds.clear();
            stopFollowing();
            view.showSelection(List.of());
        }
    }

    // La integracion llama este metodo en el hilo de Swing con cada estado recibido.
    public void updateState(List<ClickableAnimal> ownAnimals, List<ClickableAnimal> visibleAnimals,
            List<String> visibleZones, String selectedZone) {
        if (!SwingUtilities.isEventDispatchThread()) { //COMPROBAR EL HILO QUE ADMISTRA LA INTERFAZ
            report("El estado de los clics debe actualizarse en el hilo de Swing");
            return;
        }
        if (!validAnimals(ownAnimals, visibleAnimals) || visibleZones == null
                || visibleZones.stream().anyMatch(zone -> zone == null || !view.hasZone(zone))
                || new HashSet<>(visibleZones).size() != visibleZones.size()
                || (visibleZones.isEmpty() ? selectedZone != null
                        : selectedZone == null || !visibleZones.contains(selectedZone))) {
            report("Estado invalido para los clics: revisar animales y zonas");
            return;
        }
        //GUARDA LAS COPIAS DE LOS ANIMALES PROPIOS, ANIMALES VISIBLES Y ZONAS VISIBLES
        this.ownAnimals = List.copyOf(ownAnimals);
        this.visibleAnimals = List.copyOf(visibleAnimals);
        this.visibleZones = List.copyOf(visibleZones);
        this.selectedZone = selectedZone;
        stateReceived = true;
        view.showGameZones(this.visibleZones, selectedZone);
        exploringIds.removeIf(id -> this.ownAnimals.stream().noneMatch(animal ->
                animal.id() == id && animal.alive()));
        if (selectedZone == null) {
            stopFollowing();
        }
        // Elimina los IDs de animales seleccionados que ya no son visibles o están muertos
        selectedIds.removeIf(id -> this.ownAnimals.stream().noneMatch(animal ->
                animal.id() == id && animal.alive()
                        && (exploringIds.contains(id) || view.isVisiblePoint(animal.x(), animal.y()))));
        refreshSelection();
        followExplorers();
    }

    //INTERPRESATR UN CLIC DEL RATON EN EL MAPA
    @Override
    public void mouseClicked(MouseEvent event) {
        if (!interactionEnabled) {
            view.showInteractionStatus("La partida no esta activa");
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event) && !SwingUtilities.isRightMouseButton(event)) {
            return;
        }
        if (selectedZone == null) {
            view.showInteractionStatus(stateReceived
                    ? "No hay una zona visible" : "Esperando los datos de animales del servidor");
            return;
        }
        Optional<Point2D.Double> position = view.toMapPoint(event.getPoint());
        if (position.isEmpty()) {
            view.showInteractionStatus("Haz clic dentro del mapa");
            return;
        }
        Point2D.Double point = position.get();
        if (SwingUtilities.isLeftMouseButton(event) && !view.isVisiblePoint(point.x, point.y)) {
            view.showInteractionStatus("Selecciona animales dentro de la zona visible");
            return;
        }
        Optional<ClickableAnimal> own = animalAt(ownAnimals, point);

        //CLIC IZQUIERDO PARA SELECCIONAR ANIMALES, CLIC DERECHO PARA ACCIONES SOBRE ANIMALES SELECCIONADOS
        if (SwingUtilities.isLeftMouseButton(event)) {
            stopFollowing();
            if (!event.isControlDown()) {
                selectedIds.clear();
            }
            own.ifPresent(animal -> { // alterna la selección del animal propio clickeado
                if (!selectedIds.add(animal.id())) {
                    selectedIds.remove(animal.id());
                }
            });
            refreshSelection();
        } else if (selectedIds.isEmpty()) {
            view.showInteractionStatus("Selecciona primero uno o mas animales propios");
        } else {
            List<ClickableAnimal> targets = new ArrayList<>(ownAnimals);// crea la lista de posibles objetivos, incluyendo animales propios y visibles
            targets.addAll(visibleAnimals);// añade los animales visibles a la lista de posibles objetivos
            Optional<ClickableAnimal> target = view.isVisiblePoint(point.x, point.y)
                    ? animalAt(targets, point) : Optional.empty();
            if (target.isPresent() && ownAnimals.contains(target.get())) { // el objetivo no puede ser un animal propio
                view.showInteractionStatus("El objetivo no puede ser un animal propio");
            } else if (target.isPresent()) {
                stopFollowing();
                send("ATTACK", new AttackPayload(List.copyOf(selectedIds), target.get().id()));
            } else {
                if (send("MOVE_ANIMALS", new MovePayload(List.copyOf(selectedIds),
                        (int) Math.floor(point.x), (int) Math.floor(point.y)))) {
                    exploringIds.clear();
                    exploringIds.addAll(selectedIds);
                    requestedFollowZone = null;
                }
            }
        }
    }

    private Optional<ClickableAnimal> animalAt(List<ClickableAnimal> animals, Point2D.Double point) {// busca el animal más cercano al punto dado dentro del radio de clic
        ClickableAnimal closest = null;
        double closestDistance = UIConstants.ANIMAL_CLICK_RADIUS * UIConstants.ANIMAL_CLICK_RADIUS;
        for (ClickableAnimal animal : animals) {
            if (!animal.alive() || !view.isVisiblePoint(animal.x(), animal.y())) {
                continue;
            }
            double distance = point.distanceSq(animal.x(), animal.y());
            if (distance <= closestDistance) {
                closestDistance = distance;
                closest = animal;
            }
        }
        return Optional.ofNullable(closest);
    }
     
    // actualiza la selección de animales en la vista y muestra el estado de la interacción
    private void refreshSelection() {
        view.showSelection(ownAnimals.stream().filter(animal -> selectedIds.contains(animal.id())
                && view.isVisiblePoint(animal.x(), animal.y())).toList());
        view.showInteractionStatus(stateReceived && selectedZone == null
                ? "No hay una zona visible" : "Animales seleccionados: " + selectedIds.size());
    }

    //solicitmos el cambio de la zona seleccionada al servidor
    private void requestZone(String zone) {
        if (!interactionEnabled) {
            return;
        }
        if (selectedZone == null) {
            return; // El selector conserva su funcionamiento local en el modo de prueba.
        }
        stopFollowing();
        view.showGameZones(visibleZones, selectedZone);
        if (visibleZones.contains(zone) && !selectedZone.equals(zone)) {
            send("VIEW_ZONE", new ZonePayload(zone));
        }
    }

    private void stopFollowing() {
        exploringIds.clear();
        requestedFollowZone = null;
    }

    private void followExplorers() {
        if (!interactionEnabled || selectedZone == null || exploringIds.isEmpty()) {
            return;
        }
        if (selectedZone.equals(requestedFollowZone)) {
            requestedFollowZone = null;
        }
        // Sigue un miembro estable del grupo usando solo posiciones y zonas confirmadas.
        int leader = exploringIds.iterator().next();
        ownAnimals.stream().filter(animal -> animal.id() == leader).findFirst()
                .flatMap(animal -> MapZones.at(animal.x(), animal.y()))
                .map(zone -> zone.name())
                .filter(zone -> visibleZones.contains(zone) && !zone.equals(selectedZone)
                        && !zone.equals(requestedFollowZone))
                .ifPresent(zone -> {
                    if (send("VIEW_ZONE", new ZonePayload(zone))) {
                        requestedFollowZone = zone;
                    }
                });
    }

    //crear y cargar en el json el comando a enviar al servidor
    private <T> boolean send(String type, T payload) {// envía un comando al servidor en formato JSON
        if (messageSender == null) {
            report("No hay envio conectado: la orden no fue enviada");
            return false;
        }
        messageSender.accept(gson.toJson(new Command<>(type, payload)));
        view.showInteractionStatus("Orden entregada a la conexion: " + type);
        return true;
    }
    
    // muestra un mensaje de reporte en la consola y en la vista si es el hilo de la interfaz
    private void report(String message) {
        System.err.println(message);
        if (SwingUtilities.isEventDispatchThread()) {
            view.showInteractionStatus(message);
        }
    }

    // valida que las listas de animales propios y ajenos sean correctas y no contengan duplicados ni valores inválidos
    private static boolean validAnimals(List<ClickableAnimal> own, List<ClickableAnimal> others) {
        if (own == null || others == null) {
            return false;
        }
        List<ClickableAnimal> all = new ArrayList<>(own);
        all.addAll(others);
        Set<Integer> ids = new HashSet<>();
        return all.stream().allMatch(animal -> animal != null && Double.isFinite(animal.x())
                && Double.isFinite(animal.y()) && ids.add(animal.id()));
    }

    private record Command<T>(String type, T payload) {
    }

    private record MovePayload(List<Integer> animalIds, int targetX, int targetY) {
    }

    private record AttackPayload(List<Integer> animalIds, int targetId) {
    }

    private record ZonePayload(String zone) {
    }
}