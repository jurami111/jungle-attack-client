package cr.ac.tec.jungleattack.client.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import cr.ac.tec.jungleattack.client.model.dto.ServerMessage;
import cr.ac.tec.jungleattack.client.model.dto.ServerMessage.*;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    @Test
    void notifiesObserversAndClearsEarlierMatchOnDisconnectAndReuse() {
        GameState state = new GameState();
        AtomicInteger changes = new AtomicInteger();
        state.addObserver(value -> {
            assertSame(state, value);
            changes.incrementAndGet();
        });
        assertEquals(GameState.Phase.DISCONNECTED, state.phase());
        state.connected();
        state.apply(new ServerMessage("MATCH_CREATED", new MatchCode("AB12")));
        state.apply(new ServerMessage("PLAYER_JOINED", new PlayerJoined("Luis")));
        state.apply(new ServerMessage("ANIMAL_SELECTION",
                new AnimalSelection(List.of("MONO"), null, "RANA")));
        assertEquals(GameState.Phase.SELECTING, state.phase());
        assertEquals("Luis", state.opponent());
        state.apply(new ServerMessage("ERROR", new ServerError("No disponible")));
        assertEquals(GameState.Phase.SELECTING, state.phase());
        assertTrue(state.status().contains("No disponible"));
        state.apply(new ServerMessage("MATCH_CANCELLED", new MatchCancelled("Salio el rival")));
        assertEquals(GameState.Phase.FINISHED, state.phase());
        assertNull(state.result());
        state.returnHome();
        assertNull(state.selection());
        assertEquals("", state.code());
        assertEquals("", state.opponent());
        state.disconnected("Sin red");
        assertEquals(GameState.Phase.DISCONNECTED, state.phase());
        state.connected();
        assertEquals(GameState.Phase.HOME, state.phase());
        assertEquals(9, changes.get());
    }
}
