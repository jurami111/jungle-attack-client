package cr.ac.tec.jungleattack.client.model;

import java.util.ArrayList;
import java.util.List;

import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;

public final class GameState {
    public enum Phase { DISCONNECTED, HOME, WAITING, SELECTING, PLAYING, FINISHED }

    private final List<ModelObserver> observers = new ArrayList<>();
    private Phase phase = Phase.DISCONNECTED;
    private String code = "";
    private String opponent = "";
    private String status = "Esperando conexion con el servidor";
    private AnimalSelection selection;
    private Snapshot snapshot;
    private GameOver result;

    public void addObserver(ModelObserver observer) {
        observers.add(observer);
    }

    public Phase phase() { return phase; }
    public String code() { return code; }
    public String opponent() { return opponent; }
    public String status() { return status; }
    public AnimalSelection selection() { return selection; }
    public Snapshot snapshot() { return snapshot; }
    public GameOver result() { return result; }

    public void connected() {
        clearMatch();
        phase = Phase.HOME;
        status = "Conexion disponible: crea una partida o ingresa un codigo";
        notifyObservers();
    }

    public void disconnected(String reason) {
        clearMatch();
        phase = Phase.DISCONNECTED;
        status = reason;
        notifyObservers();
    }

    public void report(String message) {
        status = message;
        notifyObservers();
    }

    public void returnHome() {
        clearMatch();
        phase = Phase.HOME;
        status = "Crea una partida o ingresa un codigo";
        notifyObservers();
    }

    public void apply(ServerMessage message) {
        switch (message.payload()) {
            case MatchCode data -> {
                clearMatch();
                code = data.code();
                phase = Phase.WAITING;
                status = "Partida " + code + ": esperando al otro jugador";
            }
            case PlayerJoined data -> {
                opponent = data.nickname();
                status = "Se unio " + opponent;
            }
            case AnimalSelection data -> {
                selection = data;
                phase = Phase.SELECTING;
                status = data.yourSpecies() == null
                        ? "Elige tu animal" : "Animal confirmado: " + data.yourSpecies() + ". Esperando al rival";
            }
            case Snapshot data -> {
                snapshot = data;
                code = data.matchCode();
                phase = Phase.PLAYING;
                status = data.running() ? "Partida en curso" : "Esperando resultado de la partida";
            }
            case GameOver data -> {
                result = data;
                phase = Phase.FINISHED;
                status = "Partida terminada";
            }
            case MatchCancelled data -> {
                snapshot = null;
                result = null;
                phase = Phase.FINISHED;
                status = "Partida cancelada: " + data.reason();
            }
            case ServerError data -> status = "Servidor: " + data.message();
        }
        notifyObservers();
    }

    private void clearMatch() {
        code = "";
        opponent = "";
        selection = null;
        snapshot = null;
        result = null;
    }

    private void notifyObservers() {
        List.copyOf(observers).forEach(observer -> observer.onStateChanged(this));
    }
}