package cr.ac.tec.jungleattack.client.view.utils;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import cr.ac.tec.jungleattack.client.config.UIConstants;

public final class EnergyBar {
    private EnergyBar() {
    }

    public static void paint(Graphics2D graphics, double centerX, double topY, int energy, int maximumEnergy) {
        if (maximumEnergy <= 0) {
            System.err.println("La energia maxima debe ser mayor que cero");
            return;
        }

        double x = centerX - UIConstants.ENERGY_BAR_WIDTH / 2.0;
        int border = 1;
        double innerWidth = UIConstants.ENERGY_BAR_WIDTH - border * 2.0;
        double innerHeight = UIConstants.ENERGY_BAR_HEIGHT - border * 2.0;
        int boundedEnergy = Math.max(0, Math.min(energy, maximumEnergy));
        double filledWidth = innerWidth * boundedEnergy / maximumEnergy;

        graphics.setColor(UIConstants.ENERGY_BAR_BORDER_COLOR);
        graphics.fill(new Rectangle2D.Double(x, topY, UIConstants.ENERGY_BAR_WIDTH,
                UIConstants.ENERGY_BAR_HEIGHT));
        graphics.setColor(UIConstants.ENERGY_BAR_EMPTY_COLOR);
        graphics.fill(new Rectangle2D.Double(x + border, topY + border, innerWidth, innerHeight));
        graphics.setColor(UIConstants.ENERGY_BAR_FILL_COLOR);
        graphics.fill(new Rectangle2D.Double(x + border, topY + border, filledWidth, innerHeight));
    }
}
