package cr.ac.tec.jungleattack.client.controller;// controlador que maneja la animación de los animales en el mapa

import java.awt.geom.Point2D;// clase que representa un punto en el espacio 2D
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import javax.swing.SwingUtilities;// utilidades de Swing para manejar el hilo de eventos
import javax.swing.Timer;

import cr.ac.tec.jungleattack.client.config.UIConstants;
import cr.ac.tec.jungleattack.client.model.Connection;
import cr.ac.tec.jungleattack.client.view.utils.SpriteAnimation;

public final class Animator {
    public static final double DOWN = Math.PI / 2;
    private final Timer timer;
    private final Consumer<State> onUpdate;
    private final LongSupplier clock;
    private List<Point2D.Double> path;
    private double[] cumulativeLengths;
    private double totalLength;
    private double currentX;
    private double currentY;
    private long startedAt;
    private long durationNanos;
    private SpriteAnimation animation;
    private List<Integer> hiddenSegments = List.of();
    private boolean currentVisible = true;
    // angulo en radianes de la direccion del movimiento: 0 derecha, PI/2 abajo, PI izquierda, -PI/2 arriba
    private double currentHeading = DOWN;

    public Animator(Consumer<State> onUpdate) { // constructor que recibe un consumidor de estado y utiliza el reloj del sistema
        this(onUpdate, System::nanoTime);
    }

    Animator(Consumer<State> onUpdate, LongSupplier clock) { // constructor que recibe un consumidor de estado y un proveedor de tiempo
        this.onUpdate = onUpdate;
        this.clock = clock;
        timer = new Timer(UIConstants.ANIMATION_DELAY_MS, event -> update());// este timer se encarga de actualizar la animación a intervalos regulares
    }

    public boolean start(Point2D.Double start, Point2D.Double end,
            SpriteAnimation spriteAnimation, int durationMs) {
        return start(Arrays.asList(start, end), spriteAnimation, durationMs);
    }

    public boolean start(List<Point2D.Double> points,
            SpriteAnimation spriteAnimation, int durationMs) {
        return startMovement(points, List.of(), spriteAnimation, durationMs, false);
    }

    public boolean startAtSpeed(List<Point2D.Double> points,
            SpriteAnimation spriteAnimation, double unitsPerSecond) {
        return startMovement(points, List.of(), spriteAnimation, unitsPerSecond, true);
    }

    public boolean startAtSpeed(Connection connection,
            SpriteAnimation spriteAnimation, double unitsPerSecond) {
        if (connection == null) {
            System.err.println("No se puede iniciar una conexion nula");
            return false;
        }
        return startMovement(connection.pathPoints(), connection.hiddenSegments(),
                spriteAnimation, unitsPerSecond, true);
    }

    private boolean startMovement(List<Point2D.Double> points,
            List<Integer> hidden,
            SpriteAnimation spriteAnimation, double timing, boolean useSpeed) {
        if (!SwingUtilities.isEventDispatchThread()) {
            System.err.println("El movimiento debe iniciarse en el hilo de Swing");
            return false;
        }
        if (timer.isRunning()) {
            System.err.println("Ya hay un movimiento en curso");
            return false;
        }
        if (points == null || points.size() < 2
                || points.stream().anyMatch(point -> point == null
                        || !Double.isFinite(point.x) || !Double.isFinite(point.y))
                || !Double.isFinite(timing) || timing <= 0
                || spriteAnimation == null || spriteAnimation.idleImage() == null
                || spriteAnimation.frames().isEmpty() || spriteAnimation.frameDurationMs() <= 0) {
            System.err.println("Movimiento invalido: requiere puntos finitos, imagenes y duracion o velocidad finita positiva");
            return false;
        }
        if (hidden.stream().anyMatch(segment -> segment < 1 || segment >= points.size())) {
            System.err.println("Movimiento invalido: numero de tramo oculto fuera del recorrido");
            return false;
        }
        List<Point2D.Double> copied = new ArrayList<>();
        double[] lengths = new double[points.size()];
        double length = 0;
        for (int i = 0; i < points.size(); i++) {
            Point2D.Double point = points.get(i);
            copied.add(new Point2D.Double(point.x, point.y));
            if (i > 0) {
                length += copied.get(i - 1).distance(point);
                lengths[i] = length;
            }
        }
        if (!Double.isFinite(length) || length <= 0) {
            System.err.println("Movimiento invalido: la longitud del recorrido debe ser finita y positiva");
            return false;
        }
        double requestedNanos = useSpeed
                ? (length / timing) * 1_000_000_000.0
                : timing * 1_000_000.0;
        if (!Double.isFinite(requestedNanos) || requestedNanos < 1
                || requestedNanos >= Long.MAX_VALUE) {
            System.err.println("Movimiento invalido: la duracion calculada no se puede representar en nanosegundos");
            return false;
        }
        path = List.copyOf(copied);
        cumulativeLengths = lengths;
        totalLength = length;
        animation = spriteAnimation;
        hiddenSegments = List.copyOf(hidden);
        durationNanos = Math.round(requestedNanos);
        startedAt = clock.getAsLong();
        timer.start();
        update();
        return true;
    }

    void update() { // actualiza la posicion y el estado del animador
        if (!timer.isRunning()) { // si el temporizador no esta corriendo, no se actualiza
            return;
        }
        long elapsed = Math.max(0, clock.getAsLong() - startedAt); // tiempo transcurrido desde el inicio de la animacion 
        double progress = Math.min(1.0, elapsed / (double) durationNanos); // progreso de la animacion 
        if (progress >= 1.0) {
            currentX = path.getLast().x;
            currentY = path.getLast().y;
            currentVisible = !hiddenSegments.contains(path.size() - 1);
            currentHeading = headingOf(path.size() - 1);
            timer.stop();
            onUpdate.accept(new State(currentX, currentY, animation.idleImage(), false,
                    currentVisible, currentHeading));
            return;
        }
        double traveled = Math.min(Math.nextDown(totalLength), totalLength * progress);
        int segment = 1;
        while (segment < path.size() - 1 && cumulativeLengths[segment] <= traveled) {
            segment++;
        }
        Point2D.Double start = path.get(segment - 1);
        Point2D.Double end = path.get(segment);
        double segmentLength = cumulativeLengths[segment] - cumulativeLengths[segment - 1];
        double segmentProgress = (traveled - cumulativeLengths[segment - 1]) / segmentLength;
        currentVisible = !hiddenSegments.contains(segment);
        currentX = start.x + (end.x - start.x) * segmentProgress;
        currentY = start.y + (end.y - start.y) * segmentProgress;
        currentHeading = Math.atan2(end.y - start.y, end.x - start.x);
        long frameNumber = elapsed / (animation.frameDurationMs() * 1_000_000L);
        int index = (int) (frameNumber % animation.frames().size());
        onUpdate.accept(new State(currentX, currentY, animation.frames().get(index), true,
                currentVisible, currentHeading));
    }

    // busca hacia atras el ultimo tramo con longitud para no calcular la direccion de un tramo vacio
    private double headingOf(int segment) {
        for (int i = segment; i > 0; i--) {
            Point2D.Double start = path.get(i - 1);
            Point2D.Double end = path.get(i);
            if (start.distance(end) > 0) {
                return Math.atan2(end.y - start.y, end.x - start.x);
            }
        }
        return currentHeading;
    }

    public void stop() {
        if (!SwingUtilities.isEventDispatchThread()) {
            System.err.println("El movimiento debe detenerse en el hilo de Swing");
            return;
        }
        if (timer.isRunning()) {
            timer.stop();
            onUpdate.accept(new State(currentX, currentY, animation.idleImage(), false,
                    currentVisible, currentHeading));
        }
    }

    // heading: angulo de la direccion del movimiento en radianes, medido como Math.atan2(dy, dx)
    public record State(double x, double y, BufferedImage image, boolean moving, boolean visible,
            double heading) {
    }
}