package cr.ac.tec.jungleattack.client; // clase principal del juego, punto de entrada de la aplicación

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import cr.ac.tec.jungleattack.client.network.NetworkClient;
import cr.ac.tec.jungleattack.client.view.GameWindow;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SpriteCache spriteCache = new SpriteCache(); // crea una nueva instancia del caché de sprites

        spriteCache.getLoadErrors().forEach((name, error) ->
            System.err.println("Error loading " + name + ": " + error));
        SwingUtilities.invokeLater(() -> { // ejecuta la creación de la ventana del juego en el hilo de despacho de eventos de Swing

            GameWindow gameWindow = new GameWindow(spriteCache);
            AtomicReference<NetworkClient> networkClient = new AtomicReference<>(
                    new NetworkClient(gameWindow.getClientPanel()));
            gameWindow.getClientPanel().setLeaveHandler(() -> {
                networkClient.get().close();
                NetworkClient replacement = new NetworkClient(gameWindow.getClientPanel());
                networkClient.set(replacement);
                replacement.start();
            });
            gameWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    networkClient.get().close();
                }
            });
            gameWindow.setVisible(true);
            networkClient.get().start();
        });
    }
}