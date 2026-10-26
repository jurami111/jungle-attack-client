package cr.ac.tec.jungleattack.client.config; // constantes de la interfaz de usuario

import java.awt.Color;


//Guardamos los valores fijos de la interfaz de usuario, como el título de la ventana y sus dimensiones
public final class UIConstants {
    public static final String WINDOW_TITLE = "Jungle Attack";
    public static final int WINDOW_WIDTH = 1000;
    public static final int WINDOW_HEIGHT = 700;
    public static final Color TEST_PATH_COLOR = new Color(0, 220, 255, 180);
    public static final float TEST_PATH_WIDTH = 12f;
    // tamaño de los animales de prueba
    public static final int TEST_ANIMAL_SIZE = 48;
    public static final int ANIMATION_DELAY_MS = 33;
    public static final double TEST_MOVEMENT_SPEED = 100.0;
    // constantes del mapa
    public static final double MAP_MAX_ZOOM = 8.0;
    public static final double MAP_ZOOM_STEP = 1.2;
    //barra de energia del jugador y del bot
    public static final int MAX_PLAYER_ENERGY = 20;
    public static final int MAX_BOT_ENERGY = 10;
    public static final int ENERGY_BAR_WIDTH = 36;
    public static final int ENERGY_BAR_HEIGHT = 5;
    public static final int ENERGY_BAR_GAP = 4;
    public static final Color ENERGY_BAR_BORDER_COLOR = new Color(35, 35, 35);
    public static final Color ENERGY_BAR_EMPTY_COLOR = new Color(180, 45, 45);
    public static final Color ENERGY_BAR_FILL_COLOR = new Color(56, 190, 70);
    //costantes de los animales en el mapa
    public static final double ANIMAL_CLICK_RADIUS = TEST_ANIMAL_SIZE / 2.0; // radio de clic de los animales para reconocerlo 
    public static final Color ANIMAL_SELECTION_COLOR = Color.YELLOW;
    public static final float ANIMAL_SELECTION_WIDTH = 2f;// ancho del borde de selección de los animales
    public static final Color FOG_COLOR = Color.BLACK;
    // ancho, en unidades del mapa, del borde que se oscurece poco a poco dentro de la zona visible
    public static final double FOG_EDGE_WIDTH = 60.0;
    public static final int FOG_EDGE_STEPS = 12;
    public static final double FOG_EDGE_ARC = 160.0;

    private UIConstants() {
    }
}