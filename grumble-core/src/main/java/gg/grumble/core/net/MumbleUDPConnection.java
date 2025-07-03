package gg.grumble.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MumbleUDPConnection implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleUDPConnection.class);

    private static final int MAX_UDP_BUFFER_SIZE = 1024;

    private final DatagramChannel channel;
    private final Consumer<byte[]> onReceive;
    private final ExecutorService executor;

    private final String hostname;
    private final int port;

    public MumbleUDPConnection(String hostname, int port, Consumer<byte[]> onReceive) {
        this.hostname = hostname;
        this.port = port;
        this.onReceive = onReceive;
        try {
            this.channel = DatagramChannel.open();
        } catch (IOException e) {
            throw new RuntimeException("Unable to open UDP channel", e);
        }
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("MumbleUDPConnection-Thread");
            return t;
        });
    }

    public void connect() throws IOException {
        InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
        this.channel.configureBlocking(true);
        this.channel.connect(serverAddress);
        executor.submit(this::run);
    }

    public void close() throws IOException {
        executor.shutdownNow();
        channel.close();
    }

    public void send(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while(buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void run() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_UDP_BUFFER_SIZE);

        try {
            while (!Thread.interrupted()) {
                buffer.clear();
                SocketAddress from = channel.receive(buffer);
                if (from != null && onReceive != null) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    onReceive.accept(data);
                }
            }
        } catch (IOException e) {
            if (!executor.isShutdown()) {
                LOG.error("UDP receive exception", e);
            }
        }
    }
}
