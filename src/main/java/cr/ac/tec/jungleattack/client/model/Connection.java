package cr.ac.tec.jungleattack.client.model;// clase que representa una conexión entre zonas del juego

import java.awt.geom.Point2D;// clase que representa un punto en el camino de la conexión
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

public record Connection( // representa una conexión entre dos zonas del juego
        String fromZoneId,
        String toZoneId,
        List<Point2D.Double> pathPoints,
        List<Integer> hiddenSegments) {

    public Connection(String fromZoneId, String toZoneId, List<Point2D.Double> pathPoints) {
        this(fromZoneId, toZoneId, pathPoints, List.of());
    }

    public Connection { // constructor que asegura que la lista de puntos del camino sea inmutable
        pathPoints = List.copyOf(pathPoints);
        hiddenSegments = List.copyOf(hiddenSegments);
    }

    public Connection reversed() {
        List<Point2D.Double> reversedPoints = pathPoints.reversed().stream()
                .map(point -> new Point2D.Double(point.x, point.y)).toList();
        // Con N puntos, el tramo s pasa a ser el tramo N - s.
        List<Integer> reversedHidden = hiddenSegments.reversed().stream()
                .map(segment -> pathPoints.size() - segment).toList();
        return new Connection(toZoneId, fromZoneId, reversedPoints, reversedHidden);
    }

    public Optional<Connection> followedBy(Connection next) {
        if (next == null || pathPoints.size() < 2 || next.pathPoints().size() < 2
                || !toZoneId.equals(next.fromZoneId())
                || !pathPoints.getLast().equals(next.pathPoints().getFirst())) {
            System.err.println("No se pueden unir conexiones: requieren zona y punto de enlace coincidentes");
            return Optional.empty();
        }
        List<Point2D.Double> joined = new ArrayList<>();
        for (Point2D.Double point : pathPoints) {
            joined.add(new Point2D.Double(point.x, point.y));
        }
        for (Point2D.Double point : next.pathPoints().subList(1, next.pathPoints().size())) {
            joined.add(new Point2D.Double(point.x, point.y));
        }
        List<Integer> hidden = new ArrayList<>(hiddenSegments);
        int offset = pathPoints.size() - 1;
        for (int segment : next.hiddenSegments()) {
            hidden.add(offset + segment);
        }
        return Optional.of(new Connection(fromZoneId, next.toZoneId(), joined, hidden));
    }
}
