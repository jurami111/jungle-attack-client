package cr.ac.tec.jungleattack.client.model;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.Zone;

public final class MapZones {
    // El orden resuelve empates igual que MapFactory y MapGraph del servidor.
    public static final List<Zone> ALL = List.of(
            new Zone("Playa Conchal", 331, 327), new Zone("Monteverde", 910, 316),
            new Zone("Cerro Zurqui", 1507, 309), new Zone("Braulio Carrillo", 2105, 305),
            new Zone("Bahia Ballena", 449, 894), new Zone("Manuel Antonio", 329, 1459),
            new Zone("Tortuguero", 924, 1550), new Zone("Cerro Chirripo", 916, 1047),
            new Zone("Tamarindo", 1503, 1499), new Zone("Volcan Poas", 1514, 1042),
            new Zone("Volcan Arenal", 2114, 1050), new Zone("Cerro de la Muerte", 2110, 1219));

    private MapZones() {
    }

    public static Optional<Zone> at(double x, double y) {
        Zone nearest = null;
        double shortest = Double.POSITIVE_INFINITY;
        for (Zone zone : ALL) {
            double distance = Math.hypot(x - zone.centerX(), y - zone.centerY());
            if (distance < shortest) {
                shortest = distance;
                nearest = zone;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static Area visibleArea(String name, double width, double height) {
        Optional<Zone> selected = ALL.stream().filter(zone -> zone.name().equals(name)).findFirst();
        if (selected.isEmpty()) {
            return new Area();
        }
        Zone zone = selected.get();
        Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
        double extent = Math.hypot(width, height) * 4;
        for (Zone other : ALL) {
            if (other.equals(zone)) {
                continue;
            }
            double dx = other.centerX() - zone.centerX();
            double dy = other.centerY() - zone.centerY();
            double length = Math.hypot(dx, dy);
            dx /= length;
            dy /= length;
            double mx = (zone.centerX() + other.centerX()) / 2.0;
            double my = (zone.centerY() + other.centerY()) / 2.0;
            Path2D.Double halfPlane = new Path2D.Double();
            halfPlane.moveTo(mx - dy * extent, my + dx * extent);
            halfPlane.lineTo(mx + dy * extent, my - dx * extent);
            halfPlane.lineTo(mx + dy * extent - dx * extent, my - dx * extent - dy * extent);
            halfPlane.lineTo(mx - dy * extent - dx * extent, my + dx * extent - dy * extent);
            halfPlane.closePath();
            area.intersect(new Area(halfPlane));
        }
        return area;
    }
}
