package cr.ac.tec.jungleattack.client.view; // ventana principal del juego

import javax.swing.JFrame;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

public class GameWindow extends JFrame {


    public GameWindow(SpriteCache spriteCache) { // creamos una instancia de la ventana del juego


    setTitle(UIConstants.WINDOW_TITLE);
    setSize(UIConstants.WINDOW_WIDTH, UIConstants.WINDOW_HEIGHT);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);// guarda el cache 
    setContentPane(new MapPanel(spriteCache));
    setLocationRelativeTo(null); // centra la ventana en la pantalla
}
}