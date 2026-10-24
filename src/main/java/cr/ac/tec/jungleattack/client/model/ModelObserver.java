package cr.ac.tec.jungleattack.client.model;

@FunctionalInterface
public interface ModelObserver {
    void onStateChanged(GameState state);
}