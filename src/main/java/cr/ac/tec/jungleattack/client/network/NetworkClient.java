package cr.ac.tec.jungleattack.client.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import cr.ac.tec.jungleattack.client.view.ClientPanel;

public final class NetworkClient implements AutoCloseable {
    private final ClientPanel clientPanel;
    private final BlockingQueue<String> outgoing = new LinkedBlockingQueue<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile Socket socket;
    private volatile Thread readerThread;
    private volatile Thread writerThread;

    public NetworkClient(ClientPanel clientPanel) {
        this.clientPanel = clientPanel;
    }

    public void start() {
        readerThread = new Thread(this::connectAndRead, "jungle-attack-tcp-reader");
        readerThread.start();
    }

    private void connectAndRead() {
        try (Socket connection = new Socket("localhost", 12345)) {
            if (stopped.get()) {
                return;
            }
            socket = connection;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
            writerThread = new Thread(() -> writeMessages(writer), "jungle-attack-tcp-writer");
            writerThread.start();
            clientPanel.setMessageSender(outgoing::offer);

            String line;
            while (!stopped.get() && (line = reader.readLine()) != null) {
                clientPanel.receiveServerLine(line);
            }
            disconnect("el servidor cerro la conexion");
        } catch (IOException exception) {
            disconnect(exception.getMessage() == null ? exception.toString() : exception.getMessage());
        }
    }

    private void writeMessages(BufferedWriter writer) {
        try {
            while (!stopped.get()) {
                String message = outgoing.take();
                writer.write(message);
                writer.newLine();
                writer.flush();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            if (!stopped.get()) {
                disconnect(exception.getMessage() == null ? exception.toString() : exception.getMessage());
            }
        }
    }

    private void disconnect(String reason) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        closeSocket();
        Thread writer = writerThread;
        if (writer != null) {
            writer.interrupt();
        }
        clientPanel.connectionClosed(reason);
    }

    private void closeSocket() {
        Socket connection = socket;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException exception) {
                System.err.println("Error cerrando la conexion TCP: " + exception.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (stopped.compareAndSet(false, true)) {
            closeSocket();
            Thread reader = readerThread;
            if (reader != null) {
                reader.interrupt();
            }
            Thread writer = writerThread;
            if (writer != null) {
                writer.interrupt();
            }
        }
    }
}