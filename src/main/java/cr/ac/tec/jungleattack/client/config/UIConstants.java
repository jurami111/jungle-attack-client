package cr.ac.tec.jungleattack.client.config; // constantes de la interfaz de usuario

import java.awt.Color;


//Guardamos los valores fijos de la interfaz de usuario, como el título de la ventana y sus dimensiones
public final class UIConstants {
    public static final String WINDOW_TITLE = "Jungle Attack";
    public static final int WINDOW_WIDTH = 1000;
    public static final int WINDOW_HEIGHT = 700;
    public static final Color TEST_PATH_COLOR = new Color(0, 220, 255, 180);
    public static final float TEST_PATH_WIDTH = 12f;
    public static final int TEST_ANIMAL_SIZE = 120;
    public static final int ANIMATION_DELAY_MS = 33;
    public static final double TEST_MOVEMENT_SPEED = 100.0;
    public static final Color FOG_COLOR = Color.BLACK;
    // ancho, en unidades del mapa, del borde que se oscurece poco a poco dentro de la zona visible
    public static final double FOG_EDGE_WIDTH = 60.0;
    public static final int FOG_EDGE_STEPS = 12;
    public static final double FOG_EDGE_ARC = 160.0;

    private UIConstants() {
    }
}