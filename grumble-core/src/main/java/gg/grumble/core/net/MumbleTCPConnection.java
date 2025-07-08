package gg.grumble.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.function.*;

public class MumbleTCPConnection implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleTCPConnection.class);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final String hostname;
    private final int port;
    private final Runnable onConnected;
    private final BiConsumer<Integer, byte[]> onFrameReceived;
    private final Consumer<String> onDisconnected;

    private final ExecutorService executor;
    private final BlockingQueue<ByteBuffer> sendQueue = new LinkedBlockingQueue<>();
    private final ByteBuffer netOutBuffer = ByteBuffer.allocate(32768);
    private final ByteBuffer netInBuffer = ByteBuffer.allocate(32768);

    private SocketChannel channel;
    private SSLEngine sslEngine;
    private Selector selector;
    private boolean handshakeComplete = false;

    public MumbleTCPConnection(String hostname,
                               int port,
                               Runnable onConnected,
                               BiConsumer<Integer, byte[]> onFrameReceived,
                               Consumer<String> onDisconnected) {
        this.hostname = hostname;
        this.port = port;
        this.onConnected = onConnected;
        this.onFrameReceived = onFrameReceived;
        this.onDisconnected = onDisconnected;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            return new Thread(r, "MumbleTCPConnection-Thread");
        });
    }

    public void connect() {
        connect(defaultSSLContext());
    }

    public void connect(SSLContext sslContext) {
        executor.execute(() -> {
            try {
                channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.connect(new InetSocketAddress(hostname, port));

                selector = Selector.open();
                channel.register(selector, SelectionKey.OP_CONNECT);

                sslEngine = sslContext.createSSLEngine(hostname, port);
                sslEngine.setUseClientMode(true);
                sslEngine.beginHandshake();

                runLoop();
            } catch (Exception e) {
                LOG.error("Exception during TCP run loop", e);
                onDisconnected.accept("Connection error: " + e.getMessage());
                close();
            }
        });
    }

    // Handles initial connection completion
    private void handleConnect(SelectionKey key) throws IOException {
        if (channel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } else {
            throw new IOException("Failed to finish connection");
        }
    }

    private void runLoop() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            selector.select();

            if (!selector.isOpen()) break;

            for (SelectionKey key : selector.selectedKeys()) {
                if (!key.isValid()) continue;

                if (key.isConnectable()) {
                    handleConnect(key);
                }
                if (!handshakeComplete) {
                    doHandshake(key);
                } else {
                    if (key.isWritable()) {
                        handleWrite(key);
                    }
                    if (key.isReadable()) {
                        handleRead();
                    }
                }
            }
            selector.selectedKeys().clear();
        }

        LOG.info("TCP listener thread exited");
        onDisconnected.accept("Disconnected");
    }

    private final ByteBuffer appInBuffer = ByteBuffer.allocate(32768);

    private void doHandshake(SelectionKey key) throws IOException {
        SSLEngineResult.HandshakeStatus status = sslEngine.getHandshakeStatus();
        while (!handshakeComplete) {
            switch (status) {
                case NEED_UNWRAP -> {
                    int read = channel.read(netInBuffer);
                    if (read == -1) throw new EOFException("Connection closed during handshake");
                    netInBuffer.flip();
                    SSLEngineResult result = sslEngine.unwrap(netInBuffer, appInBuffer);
                    netInBuffer.compact();
                    status = result.getHandshakeStatus();
                    switch (result.getStatus()) {
                        case OK, BUFFER_UNDERFLOW -> {}
                        case CLOSED -> {
                            close();
                            return;
                        }
                        default -> throw new IOException("Unhandled unwrap during handshake: " + result.getStatus());
                    }
                }
                case NEED_WRAP -> {
                    netOutBuffer.clear();
                    SSLEngineResult result = sslEngine.wrap(EMPTY_BUFFER, netOutBuffer);
                    netOutBuffer.flip();
                    while (netOutBuffer.hasRemaining()) {
                        channel.write(netOutBuffer);
                    }
                    status = result.getHandshakeStatus();
                }
                case NEED_TASK -> {
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    status = sslEngine.getHandshakeStatus();
                }
                case FINISHED, NOT_HANDSHAKING -> {
                    handshakeComplete = true;
                    key.interestOps(SelectionKey.OP_READ);
                    onConnected.run();
                    return;
                }
            }
        }
    }

    private void handleRead() throws IOException {
        int bytesRead = channel.read(netInBuffer);
        if (bytesRead == -1) {
            close();
            return;
        }

        netInBuffer.flip();

        while (true) {
            SSLEngineResult result = sslEngine.unwrap(netInBuffer, appInBuffer);
            switch (result.getStatus()) {
                case OK -> {
                    appInBuffer.flip();
                    while (appInBuffer.remaining() >= 6) {
                        appInBuffer.mark();
                        int type = appInBuffer.getShort() & 0xFFFF;
                        int length = appInBuffer.getInt();
                        if (appInBuffer.remaining() < length) {
                            appInBuffer.reset();
                            break;
                        }
                        byte[] payload = new byte[length];
                        appInBuffer.get(payload);
                        onFrameReceived.accept(type, payload);
                    }
                    appInBuffer.compact();
                    if (netInBuffer.hasRemaining()) {
                        continue;
                    } else {
                        netInBuffer.compact();
                        return;
                    }
                }
                case BUFFER_UNDERFLOW -> {
                    netInBuffer.compact();
                    return;
                }
                case CLOSED -> {
                    close();
                    return;
                }
                default -> throw new IOException("Unhandled SSL unwrap result: " + result.getStatus());
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        ByteBuffer plain;
        while ((plain = sendQueue.poll()) != null) {
            netOutBuffer.clear();
            SSLEngineResult result = sslEngine.wrap(plain, netOutBuffer);
            switch (result.getStatus()) {
                case OK -> {
                    netOutBuffer.flip();
                    while (netOutBuffer.hasRemaining()) {
                        channel.write(netOutBuffer);
                    }
                }
                case BUFFER_OVERFLOW -> throw new IOException("SSL wrap buffer overflow");
                case CLOSED -> {
                    close();
                    return;
                }
                default -> throw new IOException("SSL wrap error: " + result.getStatus());
            }
        }
        key.interestOps(SelectionKey.OP_READ); // Done writing
    }

    public void send(ByteBuffer buffer) {
        if (!sendQueue.offer(buffer)) {
            LOG.warn("Send queue full, dropping message");
            return;
        }
        SelectionKey key = channel.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            wakeupSelector();
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (selector != null && selector.isOpen()) selector.close();
        } catch (IOException ignored) {}

        wakeupSelector();
        executor.shutdownNow();
        executor.close();

        handshakeComplete = false;
        netInBuffer.clear();
        netOutBuffer.clear();
        appInBuffer.clear();
    }

    private void wakeupSelector() {
        if (selector != null && selector.isOpen()) {
            selector.wakeup();
        }
    }

    private SSLContext defaultSSLContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Could not create default SSL context", e);
        }
    }
}
