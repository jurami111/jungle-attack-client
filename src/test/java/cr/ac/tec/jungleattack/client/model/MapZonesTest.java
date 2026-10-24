package cr.ac.tec.jungleattack.client.model;

import java.awt.geom.Area;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapZonesTest {
    @Test
    void everyCenterAndSamplePointUsesTheSameNearestCenterAsTheServer() {
        assertEquals(12, MapZones.ALL.size());
        for (var zone : MapZones.ALL) {
            assertEquals(zone, MapZones.at(zone.centerX(), zone.centerY()).orElseThrow());
            Area area = MapZones.visibleArea(zone.name(), 2400, 1792);
            assertTrue(area.contains(zone.centerX(), zone.centerY()));
            for (int y = 17; y < 1792; y += 47) {
                for (int x = 13; x < 2400; x += 53) {
                    assertEquals(MapZones.at(x, y).orElseThrow().equals(zone), area.contains(x, y),
                            zone.name() + " at " + x + "," + y);
                }
            }
        }
        assertTrue(MapZones.visibleArea(null, 2400, 1792).isEmpty());
        assertTrue(MapZones.visibleArea("Inexistente", 2400, 1792).isEmpty());
    }

    @Test
    void tiesUseRegistrationOrderAndDoNotUseTheOldRectangles() {
        var first = MapZones.ALL.get(0);
        var second = MapZones.ALL.get(1);
        assertEquals(first, MapZones.at((first.centerX() + second.centerX()) / 2.0,
                (first.centerY() + second.centerY()) / 2.0).orElseThrow());
        assertEquals("Cerro de la Muerte", MapZones.at(2110, 1219).orElseThrow().name());
        assertEquals("Volcan Arenal", MapZones.at(2114, 1100).orElseThrow().name());
    }
}
