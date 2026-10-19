package cr.ac.tec.jungleattack.client.model; // representa una zona en el juego

import java.awt.geom.Point2D; // se utiliza para representar la posición de la zona

public record Zone(String id, String name, Point2D.Double position) { // representa una zona con un identificador, un nombre y una posición en el juego
}
