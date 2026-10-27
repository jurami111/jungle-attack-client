package cr.ac.tec.jungleattack.client.network;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import cr.ac.tec.jungleattack.client.view.ClientPanel;
import cr.ac.tec.jungleattack.client.view.utils.SpriteCache;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClientTest {
    @Test
    void closingSuppressesQueuedNotificationsFromTheAbandonedConnection() throws Exception {
        AtomicBoolean abandonedDelivered = new AtomicBoolean();
        AtomicBoolean replacementDelivered = new AtomicBoolean();
        var notifyPanel = NetworkClient.class.getDeclaredMethod("notifyPanel", Consumer.class);
        notifyPanel.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            ClientPanel panel = new ClientPanel(new SpriteCache());
            NetworkClient abandoned = new NetworkClient(panel);
            NetworkClient replacement = new NetworkClient(panel);
            try {
                notifyPanel.invoke(abandoned,
                        (Consumer<ClientPanel>) value -> abandonedDelivered.set(true));
                abandoned.close();
                notifyPanel.invoke(replacement,
                        (Consumer<ClientPanel>) value -> replacementDelivered.set(true));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertFalse(abandonedDelivered.get());
        assertTrue(replacementDelivered.get());
    }
}
