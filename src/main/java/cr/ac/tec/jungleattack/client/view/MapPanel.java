package cr.ac.tec.jungleattack.client.view; // panel que muestra el mapa del juego

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

import javax.swing.JPanel;

import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;// caché de sprites para el panel del mapa

public class MapPanel extends JPanel {

    private final Optional<BufferedImage> mapImage;

    public MapPanel(SpriteCache spriteCache) { // constructor del panel del mapa
        mapImage = spriteCache.getImage("Mapa.svg");
        setBackground(new Color(40, 90, 45));
        setOpaque(true);
    }

    @Override
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

            double scale = Math.min(// calcula la escala para ajustar la imagen del mapa al tamaño del panel
                    getWidth() / (double) image.getWidth(),
                    getHeight() / (double) image.getHeight());

            int imageWidth = (int) (image.getWidth() * scale);
            int imageHeight = (int) (image.getHeight() * scale);
            int x = (getWidth() - imageWidth) / 2;
            int y = (getHeight() - imageHeight) / 2;

            graphics2D.drawImage(image, x, y, imageWidth, imageHeight, this);
        } finally {
            graphics2D.dispose();
        }
    }
}