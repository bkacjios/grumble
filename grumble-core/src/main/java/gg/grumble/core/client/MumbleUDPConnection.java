package gg.grumble.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class MumbleUDPConnection {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleUDPConnection.class);

    private static final int MAX_UDP_BUFFER_SIZE = 1024;

    private final DatagramChannel channel;
    private final BiConsumer<SocketAddress, byte[]> onReceive;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MumbleUDPConnection(String host, int port, BiConsumer<SocketAddress, byte[]> onReceive) throws IOException {
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);
        this.onReceive = onReceive;

        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(true);
        this.channel.connect(serverAddress);
    }

    public void start() {
        executor.submit(this::run);
    }

    public void stop() throws IOException {
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
                LOG.debug("UDP recv: {} bytes from {}", buffer.position(), from);
                if (from != null && onReceive != null) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    onReceive.accept(from, data);
                }
            }
        } catch (IOException e) {
            if (!executor.isShutdown()) {
                LOG.error("UDP receive exception", e);
            }
        }
    }
}
