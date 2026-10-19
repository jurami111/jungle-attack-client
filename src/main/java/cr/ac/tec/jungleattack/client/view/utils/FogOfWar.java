package cr.ac.tec.jungleattack.client.view.utils; // dibuja la niebla de guerra sobre el mapa

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import cr.ac.tec.jungleattack.client.config.UIConstants;

public final class FogOfWar {

    private FogOfWar() {
    }

    // pinta de negro todo el mapa excepto la zona visible y oscurece su borde de forma gradual
    public static void paint(Graphics2D graphics, Rectangle2D map, Rectangle2D visible) {
        Area hidden = new Area(map);// crea un área que representa toda la zona del mapa
        hidden.subtract(new Area(visible));// resta la zona visible para obtener la zona oculta
        Color fog = UIConstants.FOG_COLOR; // obtiene el color de la niebla de guerra desde la configuración de la interfaz
        graphics.setColor(fog);
        graphics.fill(hidden);
        double band = UIConstants.FOG_EDGE_WIDTH / UIConstants.FOG_EDGE_STEPS;// calcula el ancho de cada banda del borde difuminado
        for (int step = 0; step < UIConstants.FOG_EDGE_STEPS; step++) {
            Area ring = new Area(inset(visible, step * band));//
            ring.subtract(new Area(inset(visible, (step + 1) * band)));
            int alpha = (int) Math.round(255.0 * (UIConstants.FOG_EDGE_STEPS - step)
                    / (UIConstants.FOG_EDGE_STEPS + 1));
            graphics.setColor(new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), alpha));
            graphics.fill(ring);
        }
    }

    // sin margen se usa el rectangulo exacto de la zona; hacia adentro se redondea para que el borde no sea recto
    private static Shape inset(Rectangle2D area, double amount) {
        double width = Math.max(0, area.getWidth() - 2 * amount);
        double height = Math.max(0, area.getHeight() - 2 * amount);
        if (amount == 0) {
            return new Rectangle2D.Double(area.getX(), area.getY(), width, height);
        }
        return new RoundRectangle2D.Double(area.getX() + amount, area.getY() + amount, width, height,
                UIConstants.FOG_EDGE_ARC, UIConstants.FOG_EDGE_ARC);
    }
}
