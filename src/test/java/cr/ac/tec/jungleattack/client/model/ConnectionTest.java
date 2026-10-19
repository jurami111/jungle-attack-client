package cr.ac.tec.jungleattack.client.model;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {
    @Test
    void joinsPathsWithoutDuplicateJunctionAndOffsetsHiddenSegments() {
        var first = new Connection("A", "B", List.of(new Point2D.Double(0, 0),
                new Point2D.Double(100, 0), new Point2D.Double(200, 0)), List.of(2));
        var second = new Connection("B", "C", List.of(new Point2D.Double(200, 0),
                new Point2D.Double(200, 100), new Point2D.Double(200, 300)), List.of(1));
        var joined = first.followedBy(second).orElseThrow();
        assertEquals("A", joined.fromZoneId());
        assertEquals("C", joined.toZoneId());
        assertEquals(List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0),
                new Point2D.Double(200, 0), new Point2D.Double(200, 100),
                new Point2D.Double(200, 300)), joined.pathPoints());
        assertEquals(List.of(2, 3), joined.hiddenSegments());
        assertEquals(3, first.pathPoints().size());
        assertEquals(3, second.pathPoints().size());
        joined.pathPoints().getFirst().x = 999;
        joined.pathPoints().getLast().y = 999;
        assertEquals(0, first.pathPoints().getFirst().x);
        assertEquals(300, second.pathPoints().getLast().y);
    }

    @Test
    void rejectsDisconnectedPathsInsteadOfCreatingJump() {
        var first = new Connection("A", "B",
                List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0)));
        assertTrue(first.followedBy(null).isEmpty());
        assertTrue(first.followedBy(new Connection("C", "D",
                List.of(new Point2D.Double(100, 0), new Point2D.Double(200, 0)))).isEmpty());
        assertTrue(first.followedBy(new Connection("B", "C",
                List.of(new Point2D.Double(101, 0), new Point2D.Double(200, 0)))).isEmpty());
    }

    @Test
    void reversesEndpointsPointsAndHiddenSegmentsWithoutMutatingOriginal() {
        var points = List.of(new Point2D.Double(0, 0), new Point2D.Double(50, 0),
                new Point2D.Double(50, 100), new Point2D.Double(200, 100),
                new Point2D.Double(200, 300));
        var original = new Connection("A", "B", points, List.of(1, 3));
        var reverse = original.reversed();
        assertEquals("B", reverse.fromZoneId());
        assertEquals("A", reverse.toZoneId());
        assertEquals(points.reversed(), reverse.pathPoints());
        assertEquals(List.of(2, 4), reverse.hiddenSegments());
        assertEquals(original, reverse.reversed());
        assertEquals(points, original.pathPoints());
        assertEquals(List.of(1, 3), original.hiddenSegments());
        reverse.pathPoints().getFirst().x = 999;
        assertEquals(200, original.pathPoints().getLast().x);
    }

    @Test
    void reversesVisibleConnection() {
        var connection = new Connection("A", "B",
                List.of(new Point2D.Double(10, 20), new Point2D.Double(30, 40)));
        assertTrue(connection.reversed().hiddenSegments().isEmpty());
        assertEquals(connection, connection.reversed().reversed());
    }
}
