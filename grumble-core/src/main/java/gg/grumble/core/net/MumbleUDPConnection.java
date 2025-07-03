package gg.grumble.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MumbleUDPConnection implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleUDPConnection.class);

    private static final int MAX_UDP_BUFFER_SIZE = 1024;

    private final DatagramChannel channel;
    private final Selector selector;
    private final Consumer<byte[]> onReceive;
    private final ExecutorService executor;

    private final String hostname;
    private final int port;

    private volatile boolean running = true;

    public MumbleUDPConnection(String hostname, int port, Consumer<byte[]> onReceive) {
        this.hostname = hostname;
        this.port = port;
        this.onReceive = onReceive;

        try {
            this.channel = DatagramChannel.open();
            this.channel.configureBlocking(false);
            this.channel.setOption(StandardSocketOptions.SO_RCVBUF, 512 * 1024);
            this.channel.bind(new InetSocketAddress("0.0.0.0", 0));

            this.selector = Selector.open();
            this.channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize UDP connection", e);
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("MumbleUDPConnection-Thread");
            return t;
        });
    }

    public void connect() {
        executor.submit(this::run);
    }

    public void send(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        InetSocketAddress target = new InetSocketAddress(hostname, port);
        while (buffer.hasRemaining()) {
            channel.send(buffer, target);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        selector.wakeup(); // interrupt select()
        executor.shutdownNow();
        selector.close();
        channel.close();
        LOG.info("UDP connection closed");
    }

    private void run() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_UDP_BUFFER_SIZE);

        try {
            while (running) {
                int readyChannels = selector.select(2000); // 2s timeout to avoid hangs

                if (!running) break;

                if (readyChannels == 0) {
                    LOG.warn("Selector woke up, but no channels are ready");
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    LOG.trace("SelectionKey: valid={}, readable={}", key.isValid(), key.isReadable());

                    if (!key.isValid()) {
                        LOG.warn("SelectionKey invalid, re-registering channel");
                        key.cancel();
                        channel.register(selector, SelectionKey.OP_READ);
                        continue;
                    }

                    if (key.isReadable()) {
                        buffer.clear();
                        SocketAddress from = channel.receive(buffer);

                        if (from != null && onReceive != null) {
                            buffer.flip();
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);

                            try {
                                onReceive.accept(data);
                            } catch (Exception ex) {
                                LOG.error("Error in UDP receive callback", ex);
                            }
                        }
                    }
                }

                LOG.trace("DatagramChannel open: {}", channel.isOpen());
            }
        } catch (IOException e) {
            if (running) {
                LOG.error("UDP receive error", e);
            }
        }

        LOG.info("UDP listener thread exited");
    }
}
