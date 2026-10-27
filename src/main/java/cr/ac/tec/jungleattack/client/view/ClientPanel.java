package cr.ac.tec.jungleattack.client.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.google.gson.Gson;

import cr.ac.tec.jungleattack.client.controller.GameController;
import cr.ac.tec.jungleattack.client.model.AnimalSpecies;
import cr.ac.tec.jungleattack.client.model.ClickableAnimal;
import cr.ac.tec.jungleattack.client.model.GameState;
import cr.ac.tec.jungleattack.client.model.MapZones;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;
import cr.ac.tec.jungleattack.client.network.ServerMessageParser;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

public final class ClientPanel extends JPanel {
    private final GameState model = new GameState();
    private final ServerMessageParser parser = new ServerMessageParser();
    private final Gson gson = new Gson();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JTextField nickname = new JTextField(18);
    private final JTextField code = new JTextField(6);
    private final JButton create = new JButton("Crear partida");
    private final JButton join = new JButton("Unirse");
    private final JLabel lobbyTitle = new JLabel();
    private final JComboBox<String> species = new JComboBox<>();
    private final JButton choose = new JButton("Confirmar animal");
    private final JLabel status = new JLabel();
    private final JLabel statistics = new JLabel();
    private final JLabel outcome = new JLabel();
    private final JButton again = new JButton("Volver al inicio");
    private final JButton leave = new JButton("Salir");
    private final DefaultTableModel results = new DefaultTableModel(
            new String[]{"Especie", "Bots abatidos", "Bajas propias", "Cafes"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final MapPanel map;
    private final GameController controller;
    private Consumer<String> sender;
    private Runnable leaveHandler;
    private boolean requestPending;
    private Snapshot renderedSnapshot;

    public ClientPanel(SpriteCache cache) {
        setLayout(new BorderLayout());
        map = new MapPanel(cache, false);
        controller = new GameController(map);
        controller.setInteractionEnabled(false);
        controller.setMessageSender(this::sendGameCommand);

        JPanel home = new JPanel(new FlowLayout());
        home.add(new JLabel("Apodo:"));
        home.add(nickname);
        home.add(create);
        home.add(new JLabel("Codigo:"));
        home.add(code);
        home.add(join);
        content.add(home, "home");

        JPanel lobby = new JPanel(new FlowLayout());
        lobby.add(lobbyTitle);
        lobby.add(species);
        lobby.add(choose);
        content.add(lobby, "lobby");

        JPanel game = new JPanel(new BorderLayout());
        game.add(statistics, BorderLayout.NORTH);
        game.add(map, BorderLayout.CENTER);
        content.add(game, "game");

        JPanel finished = new JPanel(new BorderLayout());
        JPanel heading = new JPanel(new GridLayout(0, 1));
        heading.add(outcome);
        heading.add(again);
        finished.add(heading, BorderLayout.NORTH);
        finished.add(new JScrollPane(new JTable(results)), BorderLayout.CENTER);
        content.add(finished, "finished");
        add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.CENTER);
        footer.add(leave, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        create.addActionListener(event -> enterMatch(true));
        join.addActionListener(event -> enterMatch(false));
        choose.addActionListener(event -> selectAnimal());
        leave.addActionListener(event -> {
            if (leaveHandler == null) {
                reportError("No hay un manejador conectado para salir de la partida");
                return;
            }
            setMessageSender(null);
            model.report("Saliendo de la partida. Reconectando...");
            leaveHandler.run();
        });
        again.addActionListener(event -> {
            if (sender != null && model.phase() == GameState.Phase.FINISHED) {
                requestPending = false;
                map.clearGame();
                renderedSnapshot = null;
                model.returnHome();
            }
        });
        model.addObserver(this::refresh);
        refresh(model);
        if (!cache.getLoadErrors().isEmpty()) {
            model.report("Error cargando recursos: " + cache.getLoadErrors());
        }
    }

    public GameController getController() {
        return controller;
    }

    public GameState getModel() {
        return model;
    }

    public void setLeaveHandler(Runnable handler) {
        onSwing(() -> {
            leaveHandler = handler;
            refresh(model);
        });
    }

    // La capa TCP entrega una funcion no bloqueante y avisa desconexion o errores por separado.
    public void setMessageSender(Consumer<String> messageSender) {
        onSwing(() -> {
            sender = messageSender;
            requestPending = false;
            map.clearGame();
            renderedSnapshot = null;
            if (messageSender == null) {
                model.disconnected("Conexion no disponible");
            } else {
                model.connected();
            }
        });
    }

    public void receiveServerLine(String line) {
        ServerMessageParser.ParseResult parsed = parser.parse(line);
        onSwing(() -> {
            if (sender == null) {
                reportError("Se recibio un mensaje sin una conexion activa");
                return;
            }
            if (parsed.error().isPresent()) {
                reportError(parsed.error().get());
                return;
            }
            ServerMessage message = parsed.message().orElseThrow();
            if (!supported(message.payload())) {
                reportError("Mensaje incompatible: revisar especies, colores y centros de zonas");
                return;
            }
            if (message.payload() instanceof ServerError) {
                controller.onAutomaticZoneRequestRejected();
            }
            requestPending = false;
            model.apply(message);
        });
    }

    public void connectionClosed(String reason) {
        onSwing(() -> {
            sender = null;
            requestPending = false;
            map.clearGame();
            model.disconnected("Desconexion: " + reason);
        });
    }

    public void reportConnectionError(String reason) {
        onSwing(() -> {
            requestPending = false;
            reportError("Conexion: " + reason);
        });
    }

    private static void onSwing(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private void enterMatch(boolean creating) {
        if (model.phase() != GameState.Phase.HOME || requestPending) {
            return;
        }
        String name = nickname.getText().strip();
        String matchCode = code.getText().strip().toUpperCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 24) {
            model.report("El apodo debe tener entre 1 y 24 caracteres");
            return;
        }
        if (!creating && !matchCode.matches("[A-Z0-9]{4}")) {
            model.report("El codigo debe tener cuatro letras o numeros");
            return;
        }
        requestPending = true;
        send(creating ? "CREATE_MATCH" : "JOIN_MATCH",
                creating ? Map.of("nickname", name) : Map.of("nickname", name, "code", matchCode));
    }

    private void selectAnimal() {
        if (model.phase() != GameState.Phase.SELECTING || requestPending
                || model.selection().yourSpecies() != null
                || !(species.getSelectedItem() instanceof String selected)) {
            return;
        }
        requestPending = true;
        send("SELECT_ANIMAL", Map.of("species", selected));
    }

    private void send(String type, Map<String, String> payload) {
        if (sender == null) {
            requestPending = false;
            reportError("No hay conexion: la orden no fue enviada");
            return;
        }
        model.report("Esperando respuesta: " + type);
        sender.accept(gson.toJson(Map.of("type", type, "payload", payload)));
    }

    private void sendGameCommand(String json) {
        if (sender == null || model.phase() != GameState.Phase.PLAYING
                || model.snapshot() == null || !model.snapshot().running()) {
            reportError("No hay una partida activa: la orden no fue enviada");
            return;
        }
        sender.accept(json);
    }

    private void reportError(String message) {
        System.err.println(message);
        model.report(message);
    }

    private void refresh(GameState state) {
        status.setText(state.status());
        leave.setEnabled(leaveHandler != null && sender != null
                && (state.phase() == GameState.Phase.WAITING
                    || state.phase() == GameState.Phase.SELECTING
                    || state.phase() == GameState.Phase.PLAYING));
        boolean home = state.phase() == GameState.Phase.HOME && sender != null && !requestPending;
        create.setEnabled(home);
        join.setEnabled(home);
        nickname.setEnabled(home);
        code.setEnabled(home);
        boolean selecting = state.phase() == GameState.Phase.SELECTING
                && state.selection() != null && state.selection().yourSpecies() == null && !requestPending;
        String previous = (String) species.getSelectedItem();
        List<String> available = state.selection() == null ? List.of() : state.selection().availableSpecies();
        boolean same = species.getItemCount() == available.size();
        for (int i = 0; same && i < available.size(); i++) {
            same = species.getItemAt(i).equals(available.get(i));
        }
        if (!same) {
            species.removeAllItems();
            available.forEach(species::addItem);
            if (previous != null && available.contains(previous)) {
                species.setSelectedItem(previous);
            }
        }
        species.setEnabled(selecting);
        choose.setEnabled(selecting && species.getSelectedItem() != null);
        lobbyTitle.setText("Partida: " + state.code()
                + (state.opponent().isBlank() ? "" : " | Rival: " + state.opponent()));
        boolean playing = state.phase() == GameState.Phase.PLAYING && state.snapshot() != null;
        controller.setInteractionEnabled(playing && state.snapshot().running());
        if (playing) {
            Snapshot snapshot = state.snapshot();
            if (snapshot != renderedSnapshot) {
                map.showSnapshot(snapshot);
                controller.updateState(clickable(snapshot.ownAnimals()), clickable(snapshot.visibleAnimals()),
                        snapshot.visibleZones().stream().map(Zone::name).toList(),
                        snapshot.selectedZone() == null ? null : snapshot.selectedZone().name());
                renderedSnapshot = snapshot;
            }
            statistics.setText("Partida " + snapshot.matchCode() + " | " + snapshot.ownPack().species()
                    + " | Vivos: " + snapshot.ownAnimals().stream().filter(Animal::alive).count()
                    + " | Dano: " + snapshot.ownPack().damageCapacity()
                    + " | Bots: " + snapshot.ownPack().botsKilled()
                    + " | Bajas: " + snapshot.ownPack().animalsEliminated()
                    + " | Cafes: " + snapshot.ownPack().cupsConsumed());
        } else {
            map.stopGameAnimation();
        }
        if (state.phase() == GameState.Phase.FINISHED) {
            results.setRowCount(0);
            if (state.result() != null) {
                GameOver result = state.result();
                outcome.setText("Ganador: " + result.winner() + " | Duracion: " + result.durationSeconds() + " s");
                result.packs().forEach(pack -> results.addRow(new Object[]{pack.species(),
                        pack.botsKilled(), pack.animalsEliminated(), pack.cupsConsumed()}));
            } else {
                outcome.setText(state.status());
            }
        }
        again.setEnabled(sender != null);
        cards.show(content, switch (state.phase()) {
            case HOME, DISCONNECTED -> "home";
            case WAITING, SELECTING -> "lobby";
            case PLAYING -> "game";
            case FINISHED -> "finished";
        });
    }

    private static List<ClickableAnimal> clickable(List<Animal> animals) {
        return animals.stream().map(animal ->
                new ClickableAnimal(animal.id(), animal.x(), animal.y(), animal.alive())).toList();
    }

    private static boolean known(String species) {
        return species == null || AnimalSpecies.find(species).isPresent();
    }

    private static boolean supported(Payload payload) {
        return switch (payload) {
            case AnimalSelection data -> data.availableSpecies().stream().allMatch(ClientPanel::known)
                    && known(data.yourSpecies()) && known(data.opponentSpecies());
            case Snapshot data -> known(data.ownPack().species())
                    && data.ownFlagColor().matches("#[0-9a-fA-F]{6}")
                    && java.util.stream.Stream.concat(data.ownAnimals().stream(), data.visibleAnimals().stream())
                        .allMatch(animal -> known(animal.species()))
                    && data.visibleZones().stream().allMatch(MapZones.ALL::contains)
                    && data.visibleFlags().stream().allMatch(flag ->
                        known(flag.owner()) && flag.color().matches("#[0-9a-fA-F]{6}"));
            case GameOver data -> known(data.winner()) && data.packs().stream().allMatch(pack -> known(pack.species()));
            default -> true;
        };
    }
}
