package cr.ac.tec.jungleattack.client.controller;

import cr.ac.tec.jungleattack.client.view.utils.SpriteAnimation;
import cr.ac.tec.jungleattack.client.model.Connection;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimatorTest {
    private final BufferedImage idle = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final BufferedImage first = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final BufferedImage second = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final SpriteAnimation animation = new SpriteAnimation(idle, List.of(first, second), 100);

    @Test
    void continuousMonteverdeZurquiPoasRouteKeepsMovingThroughHiddenJunction() throws Exception {
        List<Connection> connections = new ArrayList<>();
        try (var stream = AnimatorTest.class.getResourceAsStream("/map/connections.json");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var definitions = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("connections");
            for (int index : new int[]{2, 0}) {
                var route = definitions.get(index).getAsJsonObject();
                List<Point2D.Double> points = new ArrayList<>();
                List<Integer> hidden = new ArrayList<>();
                for (var element : route.getAsJsonArray("points")) {
                    var point = element.getAsJsonObject();
                    points.add(new Point2D.Double(point.get("x").getAsDouble(), point.get("y").getAsDouble()));
                }
                for (var segment : route.getAsJsonArray("hiddenSegments")) {
                    hidden.add(segment.getAsInt());
                }
                connections.add(new Connection(route.get("fromZoneId").getAsString(),
                        route.get("toZoneId").getAsString(), points, hidden));
            }
        }
        Connection entry = connections.getFirst();
        Connection joined = entry.followedBy(connections.getLast()).orElseThrow();
        double entryLength = 0;
        double totalLength = 0;
        for (int i = 1; i < entry.pathPoints().size(); i++) {
            entryLength += entry.pathPoints().get(i - 1).distance(entry.pathPoints().get(i));
        }
        for (int i = 1; i < joined.pathPoints().size(); i++) {
            totalLength += joined.pathPoints().get(i - 1).distance(joined.pathPoints().get(i));
        }
        final double junctionDistance = entryLength;
        final double distance = totalLength;
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            try {
                assertTrue(animator.startAtSpeed(joined, animation, 100));
                assertEquals(900, states.getLast().x());
                assertEquals(322, states.getLast().y());
                for (double delta : new double[]{-10, 0, 10}) {
                    clock.set(Math.round((junctionDistance + delta) / 100 * 1_000_000_000.0));
                    animator.update();
                    assertTrue(states.getLast().moving());
                    assertFalse(states.getLast().visible());
                    assertEquals(delta < 0 ? 1508 + delta : 1508,
                            states.getLast().x(), 0.000001);
                    assertEquals(delta < 0 ? 308 : 308 + delta,
                            states.getLast().y(), 0.000001);
                    long frame = clock.get() / 100_000_000L;
                    assertSame(animation.frames().get((int) (frame % 2)), states.getLast().image());
                }
                clock.set(Math.round((junctionDistance + 66) / 100 * 1_000_000_000.0));
                animator.update();
                assertTrue(states.getLast().visible());
                assertEquals(374, states.getLast().y(), 0.000001);
                clock.set((long) Math.ceil(distance / 100 * 1_000_000_000.0));
                animator.update();
                assertEquals(1510, states.getLast().x());
                assertEquals(1160, states.getLast().y());
                assertFalse(states.getLast().moving());
                assertTrue(states.getLast().visible());
                assertSame(idle, states.getLast().image());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void headingFollowsEachSegmentAndKeepsLastDirectionWhenFinished() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            List<Point2D.Double> square = List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0),
                    new Point2D.Double(100, 100), new Point2D.Double(0, 100), new Point2D.Double(0, 0),
                    new Point2D.Double(100, 100));
            try {
                assertTrue(animator.startAtSpeed(square, animation, 100));
                assertEquals(0, states.getLast().heading(), 0.000001);
                double[] expected = {0, Math.PI / 2, Math.PI, -Math.PI / 2, Math.PI / 4};
                for (int i = 0; i < expected.length; i++) {
                    clock.set(500_000_000L + i * 1_000_000_000L);
                    animator.update();
                    assertTrue(states.getLast().moving());
                    assertEquals(expected[i], states.getLast().heading(), 0.000001);
                }
                clock.set(10_000_000_000L);
                animator.update();
                assertFalse(states.getLast().moving());
                assertEquals(Math.PI / 4, states.getLast().heading(), 0.000001);
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void reversedRouteFollowsSameCornersAtSameSpeedAndFinishesInsideTunnel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            var route = new Connection("A", "B", List.of(new Point2D.Double(0, 0),
                    new Point2D.Double(100, 0), new Point2D.Double(100, 200)), List.of(1));
            try {
                assertTrue(animator.startAtSpeed(route.reversed(), animation, 100));
                assertEquals(100, states.getLast().x());
                assertEquals(200, states.getLast().y());
                assertTrue(states.getLast().visible());
                clock.set(1_000_000_000L);
                animator.update();
                assertEquals(100, states.getLast().x(), 0.000001);
                assertEquals(100, states.getLast().y(), 0.000001);
                assertTrue(states.getLast().visible());
                clock.set(2_500_000_000L);
                animator.update();
                assertEquals(50, states.getLast().x(), 0.000001);
                assertEquals(0, states.getLast().y(), 0.000001);
                assertTrue(states.getLast().moving());
                assertFalse(states.getLast().visible());
                clock.set(3_000_000_000L);
                animator.update();
                assertEquals(0, states.getLast().x());
                assertEquals(0, states.getLast().y());
                assertFalse(states.getLast().moving());
                assertFalse(states.getLast().visible());
                assertSame(idle, states.getLast().image());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void zurquiPoasRouteExitsTunnelAndFinishesAtSharedSouthernAnchor() throws Exception {
        List<Point2D.Double> points = new ArrayList<>();
        List<Integer> hidden = new ArrayList<>();
        try (var stream = AnimatorTest.class.getResourceAsStream("/map/connections.json");
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var route = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("connections").get(0).getAsJsonObject();
            for (var element : route.getAsJsonArray("points")) {
                var point = element.getAsJsonObject();
                points.add(new Point2D.Double(point.get("x").getAsDouble(), point.get("y").getAsDouble()));
            }
            for (var segment : route.getAsJsonArray("hiddenSegments")) {
                hidden.add(segment.getAsInt());
            }
        }
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            try {
                assertTrue(animator.startAtSpeed(
                        new Connection("CERRO_ZURQUI", "VOLCAN_POAS", points, hidden), animation, 100));
                assertFalse(states.getLast().visible());
                clock.set(280_000_000L);
                animator.update();
                assertEquals(336, states.getLast().y(), 0.000001);
                assertFalse(states.getLast().visible());
                clock.set(600_000_000L);
                animator.update();
                assertEquals(368, states.getLast().y(), 0.000001);
                assertTrue(states.getLast().visible());
                double length = 0;
                for (int i = 1; i < points.size(); i++) {
                    length += points.get(i - 1).distance(points.get(i));
                }
                clock.set((long) Math.ceil(length / 100 * 1_000_000_000.0));
                animator.update();
                assertEquals(1510, states.getLast().x());
                assertEquals(1160, states.getLast().y());
                assertTrue(states.getLast().visible());
                assertFalse(states.getLast().moving());
                assertSame(idle, states.getLast().image());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void hidesOnlyInsideTunnelWithoutPausingAndCanFinishHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            var points = List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0),
                    new Point2D.Double(300, 0), new Point2D.Double(400, 0));
            try {
                assertTrue(animator.startAtSpeed(new Connection("A", "B", points, List.of(2)), animation, 100));
                assertTrue(states.getLast().visible());
                clock.set(1_000_000_000L);
                animator.update();
                assertFalse(states.getLast().visible());
                assertEquals(100, states.getLast().x(), 0.000001);
                clock.set(2_000_000_000L);
                animator.update();
                assertEquals(200, states.getLast().x(), 0.000001);
                assertFalse(states.getLast().visible());
                assertTrue(states.getLast().moving());
                clock.set(3_000_000_000L);
                animator.update();
                assertEquals(300, states.getLast().x(), 0.000001);
                assertTrue(states.getLast().visible());
                clock.set(4_000_000_000L);
                animator.update();
                assertTrue(states.getLast().visible());
                assertFalse(states.getLast().moving());

                assertTrue(animator.startAtSpeed(new Connection("A", "B", points, List.of(3)), animation, 100));
                clock.set(8_000_000_000L);
                animator.update();
                assertFalse(states.getLast().visible());
                assertFalse(states.getLast().moving());
                assertSame(idle, states.getLast().image());

                assertTrue(animator.startAtSpeed(new Connection("A", "B", points, List.of(1)), animation, 100));
                assertFalse(states.getLast().visible());
                animator.stop();
                assertFalse(states.getLast().visible());
                assertFalse(states.getLast().moving());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void rejectsHiddenSegmentsOutsideConnection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Animator animator = new Animator(state -> fail("Invalid route must not start"), () -> 0);
            var points = List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0));
            assertFalse(animator.startAtSpeed(new Connection("A", "B", points, List.of(0)), animation, 100));
            assertFalse(animator.startAtSpeed(new Connection("A", "B", points, List.of(2)), animation, 100));
        });
    }

    @Test
    void sameSpeedProducesSameAdvanceAndProportionalArrivalTimes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> shortStates = new ArrayList<>();
            List<Animator.State> longStates = new ArrayList<>();
            Animator shortRoute = new Animator(shortStates::add, clock::get);
            Animator longRoute = new Animator(longStates::add, clock::get);
            try {
                assertTrue(shortRoute.startAtSpeed(List.of(new Point2D.Double(0, 0),
                        new Point2D.Double(100, 0)), animation, 100));
                assertTrue(longRoute.startAtSpeed(List.of(new Point2D.Double(0, 0),
                        new Point2D.Double(100, 0), new Point2D.Double(100, 300)), animation, 100));
                clock.set(500_000_000L);
                shortRoute.update();
                longRoute.update();
                assertEquals(50, shortStates.getLast().x(), 0.000001);
                assertEquals(50, longStates.getLast().x(), 0.000001);
                clock.set(1_000_000_000L);
                shortRoute.update();
                longRoute.update();
                assertFalse(shortStates.getLast().moving());
                assertEquals(100, shortStates.getLast().x());
                assertSame(idle, shortStates.getLast().image());
                assertTrue(longStates.getLast().moving());
                assertEquals(100, longStates.getLast().x(), 0.000001);
                assertEquals(0, longStates.getLast().y(), 0.000001);
                clock.set(2_000_000_000L);
                longRoute.update();
                assertEquals(100, longStates.getLast().x(), 0.000001);
                assertEquals(100, longStates.getLast().y(), 0.000001);
                clock.set(4_000_000_000L);
                longRoute.update();
                assertFalse(longStates.getLast().moving());
                assertEquals(100, longStates.getLast().x());
                assertEquals(300, longStates.getLast().y());
                assertSame(idle, longStates.getLast().image());
            } finally {
                shortRoute.stop();
                longRoute.stop();
            }
        });
    }

    @Test
    void rejectsInvalidSpeedsAndUnrepresentableDurations() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, () -> 0);
            var points = List.of(new Point2D.Double(0, 0), new Point2D.Double(100, 0));
            for (double speed : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY,
                    Double.MIN_VALUE, Double.MAX_VALUE, 1e-10}) {
                assertFalse(animator.startAtSpeed(points, animation, speed));
            }
            assertTrue(states.isEmpty());
        });
    }

    @Test
    void followsCornerWithConstantSpeedAcrossUnequalSegments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            try {
                assertTrue(animator.start(List.of(new Point2D.Double(0, 0),
                        new Point2D.Double(100, 0), new Point2D.Double(100, 300)), animation, 4000));
                long[] times = {0, 500, 1000, 1500, 2000, 3500, 4000, 5000};
                double[][] positions = {{0, 0}, {50, 0}, {100, 0}, {100, 50},
                        {100, 100}, {100, 250}, {100, 300}, {100, 300}};
                for (int i = 0; i < times.length; i++) {
                    clock.set(times[i] * 1_000_000L);
                    animator.update();
                    assertEquals(positions[i][0], states.getLast().x(), 0.000001);
                    assertEquals(positions[i][1], states.getLast().y(), 0.000001);
                    if (times[i] < 4000) assertTrue(states.getLast().moving());
                }
                assertFalse(states.getLast().moving());
                assertSame(idle, states.getLast().image());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void skipsRepeatedPointsAndCopiesRouteCoordinates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            var a = new Point2D.Double(0, 0);
            var b = new Point2D.Double(100, 0);
            var c = new Point2D.Double(100, 300);
            var route = new ArrayList<>(List.of(a, a, b, b, c, c));
            try {
                assertTrue(animator.start(route, animation, 4000));
                a.setLocation(999, 999);
                b.setLocation(999, 999);
                c.setLocation(999, 999);
                route.clear();
                clock.set(1_000_000_000L);
                animator.update();
                assertEquals(100, states.getLast().x());
                assertEquals(0, states.getLast().y());
                clock.set(3_000_000_000L);
                animator.update();
                assertEquals(100, states.getLast().x());
                assertEquals(200, states.getLast().y());
                clock.set(4_000_000_000L);
                animator.update();
                assertEquals(100, states.getLast().x());
                assertEquals(300, states.getLast().y());
                assertFalse(states.getLast().moving());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void rejectsMissingInvalidAndZeroLengthRoutes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, () -> 0);
            var point = new Point2D.Double(1, 2);
            assertFalse(animator.start(null, animation, 1000));
            assertFalse(animator.start(List.of(point), animation, 1000));
            assertFalse(animator.start(java.util.Arrays.asList(point, null), animation, 1000));
            assertFalse(animator.start(List.of(point, point, point), animation, 1000));
            assertFalse(animator.start(List.of(point, new Point2D.Double(Double.POSITIVE_INFINITY, 0)),
                    animation, 1000));
            assertFalse(animator.start(List.of(new Point2D.Double(-Double.MAX_VALUE, 0),
                    new Point2D.Double(Double.MAX_VALUE, 0)), animation, 1000));
            assertTrue(states.isEmpty());
        });
    }

    @Test
    void interpolatesByElapsedTimeAndStopsAtExactEndWithIdleImage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong(1000);
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            try {
                assertTrue(animator.start(new Point2D.Double(10, 20),
                        new Point2D.Double(30, 60), animation, 1000));
                Animator.State start = states.getLast();
                assertEquals(10, start.x());
                assertEquals(20, start.y());
                assertSame(first, start.image());
                assertTrue(start.moving());
                clock.addAndGet(150_000_000);
                animator.update();
                assertSame(second, states.getLast().image());
                clock.addAndGet(350_000_000);
                animator.update();
                assertEquals(20, states.getLast().x());
                assertEquals(40, states.getLast().y());
                clock.addAndGet(700_000_000);
                animator.update();
                Animator.State end = states.getLast();
                assertEquals(30, end.x());
                assertEquals(60, end.y());
                assertSame(idle, end.image());
                assertFalse(end.moving());
                int count = states.size();
                animator.update();
                assertEquals(count, states.size());
                assertTrue(animator.start(new Point2D.Double(10, 20),
                        new Point2D.Double(30, 60), animation, 1000));
                assertEquals(10, states.getLast().x());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void rejectsInvalidInputAndConcurrentStartsAndStopsInPlace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong();
            List<Animator.State> states = new ArrayList<>();
            Animator animator = new Animator(states::add, clock::get);
            Point2D.Double start = new Point2D.Double(10, 20);
            Point2D.Double end = new Point2D.Double(30, 60);
            try {
                assertFalse(animator.start(start, end, animation, 0));
                assertFalse(animator.start(new Point2D.Double(Double.NaN, 0), end, animation, 100));
                assertTrue(states.isEmpty());
                assertTrue(animator.start(start, end, animation, 1000));
                assertFalse(animator.start(start, end, animation, 1000));
                clock.set(500_000_000);
                animator.update();
                animator.stop();
                assertEquals(20, states.getLast().x());
                assertEquals(40, states.getLast().y());
                assertSame(idle, states.getLast().image());
                assertFalse(states.getLast().moving());
                int count = states.size();
                animator.update();
                assertEquals(count, states.size());
            } finally {
                animator.stop();
            }
        });
    }

    @Test
    void swingTimerReallyCompletesOnEventDispatchThread() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        List<Animator.State> states = new ArrayList<>();
        Animator[] holder = new Animator[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new Animator(state -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                states.add(state);
                if (!state.moving()) finished.countDown();
            });
            holder[0].start(new Point2D.Double(0, 0), new Point2D.Double(10, 20), animation, 100);
        });
        try {
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(10, states.getLast().x());
                assertEquals(20, states.getLast().y());
                assertSame(idle, states.getLast().image());
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> holder[0].stop());
        }
    }
}
