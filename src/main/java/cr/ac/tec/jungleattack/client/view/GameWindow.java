package cr.ac.tec.jungleattack.client.view; // ventana principal del juego

import javax.swing.JFrame;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.controller.GameController;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

public class GameWindow extends JFrame {
    private final GameController controller;


    public GameWindow(SpriteCache spriteCache) { // creamos una instancia de la ventana del juego


    setTitle(UIConstants.WINDOW_TITLE);
    setSize(UIConstants.WINDOW_WIDTH, UIConstants.WINDOW_HEIGHT);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);// CONFIGURA EL CIERRE 
    MapPanel mapPanel = new MapPanel(spriteCache);
    controller = new GameController(mapPanel);
    setContentPane(mapPanel);
    setLocationRelativeTo(null); // centra la ventana en la pantalla
}

    public GameController getController() {
        return controller;
    }
}