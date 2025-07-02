package gg.grumble.core.client;

import club.minnced.opus.util.OpusLibrary;
import com.google.protobuf.*;
import gg.grumble.core.crypto.MumbleOCB2;
import gg.grumble.core.enums.MumbleClientType;
import gg.grumble.core.enums.MumbleMessageType;
import gg.grumble.core.enums.MumblePacketTypeLegacy;
import gg.grumble.core.enums.MumblePacketTypeProtobuf;
import gg.grumble.core.models.MumbleChannel;
import gg.grumble.core.models.MumbleUser;
import gg.grumble.core.opus.OpusDecoder;
import gg.grumble.core.utils.ExceptionUtils;
import gg.grumble.core.utils.MumbleVarInt;
import gg.grumble.mumble.MumbleProto;
import gg.grumble.mumble.MumbleUDPProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MumbleClient {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleClient.class);

    private static final int UDP_TCP_FALLBACK = 2;

    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_DURATION_MS = 20;
    private static final int CHANNELS = 2;
    private static final int SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000;
    private static final int SAMPLES_PER_FRAME_TOTAL = SAMPLES_PER_FRAME * CHANNELS;

    private static final int MUMBLE_VERSION_MAJOR = 1;
    private static final int MUMBLE_VERSION_MINOR = 5;
    private static final int MUMBLE_VERSION_PATCH = 735;

    private static final int UDP_BUFFER_MAX = 1024;

    private static final float PING_MILLIS = 1e6f;
    private static final int PING_PERIOD_SECONDS = 5;

    private static final int MUMBLE_VERSION_v1 = MUMBLE_VERSION_MAJOR << 16 | MUMBLE_VERSION_MINOR << 8 | MUMBLE_VERSION_PATCH;
    private static final long MUMBLE_VERSION_V2 = ((long) MUMBLE_VERSION_MAJOR << 48)
            | ((long) MUMBLE_VERSION_MINOR << 32) | ((long) MUMBLE_VERSION_PATCH << 16);

    private final String hostname;
    private final int port;

    private MumbleUDPConnection udpConnection;

    private SocketChannel socketChannel;
    private SSLEngine sslEngine;
    private Selector selector;

    private ByteBuffer appInBuffer;
    private ByteBuffer appOutBuffer;
    private ByteBuffer netInBuffer;
    private ByteBuffer netOutBuffer;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<Class<? extends MumbleEvents.MumbleEvent>, List<MumbleEventListener<?>>> listeners = new HashMap<>();

    private final Queue<ByteBuffer> outboundPlaintextQueue = new ArrayDeque<>();

    private MumbleUser self;

    private final Map<Long, MumbleUser> users = new HashMap<>();
    private final Map<Long, MumbleChannel> channels = new HashMap<>();
    private final Map<Long, List<MumbleChannel>> childrenByParent = new HashMap<>();
    private final Map<Long, List<MumbleUser>> usersInChannel = new HashMap<>();

    private boolean synced = false;

    private boolean legacyConnection = false;
    private boolean tcpUdpTunnel = true;
    private int udpPingAccumulator = 0;

    private int tcpPingPackets = 0;
    private float tcpPingAverage = 0;
    private float tcpPingDeviation = 0;

    private int udpPingPackets = 0;
    private float udpPingAverage = 0;
    private float udpPingDeviation = 0;

    private final MumbleOCB2 crypto = new MumbleOCB2();
    private final OpusDecoder opusDecoder;

    private final SourceDataLine audioOutput;

    private volatile boolean connected = false;

    public MumbleClient(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;

        try {
            OpusLibrary.loadFromJar();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load opus library", e);
        }

        this.audioOutput = initAudioOutput();

        this.opusDecoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
    }

    private void processUdpMessage(SocketAddress socketAddress, byte[] encrypted) {
        if (crypto.isInitialized()) {
            byte[] decrypted = crypto.decrypt(encrypted);
            onUdpTunnel(ByteBuffer.wrap(decrypted), true);
        }
    }

    public MumbleClient(String hostname) {
        this(hostname, 64738);
    }

    public <T extends MumbleEvents.MumbleEvent> void addEventListener(Class<T> eventType, MumbleEventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public <T extends MumbleEvents.MumbleEvent> void removeEventListener(Class<T> eventType, MumbleEventListener<T> listener) {
        List<MumbleEventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            if (eventListeners.isEmpty()) {
                listeners.remove(eventType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void fireEvent(T event) {
        List<MumbleEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (MumbleEventListener<?> listener : eventListeners) {
                ((MumbleEventListener<T>) listener).onEvent(event);
            }
        }
    }

    private void connectUdp() {
        try {
            udpConnection = new MumbleUDPConnection(hostname, port, this::processUdpMessage);
            udpConnection.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create UDP connection", e);
        }
    }

    private SourceDataLine initAudioOutput() {
        AudioFormat format = new AudioFormat(
                48000,              // Sample rate
                16,                 // Sample size in bits
                2,                  // Channels (stereo)
                true,               // Signed
                false               // Little-endian
        );

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            return line;
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Failed to initialize audio output", e);
        }
    }


    private void onConnectedTcp() {
        connectUdp();
        sendVersion();
        fireEvent(new MumbleEvents.Connected());
    }

    private void onServerVersion(MumbleProto.Version version) {
        legacyConnection = !version.hasVersionV2();

        String release = version.getRelease();
        if (legacyConnection) {
            int v1 = version.getVersionV1();
            int major = (v1 >> 16) & 0xFF;
            int minor = (v1 >> 8) & 0xFF;
            int patch = v1 & 0xFF;
            LOG.info("Server Version v1: {}.{}.{} ({})", major, minor, patch, release);
        } else {
            long v2 = version.getVersionV2();
            long major = (v2 >> 48) & 0xFFFF;
            long minor = (v2 >> 32) & 0xFFFF;
            long patch = (v2 >> 16) & 0xFFFF;
            LOG.info("Server Version v2: {}.{}.{} ({})", major, minor, patch, release);
        }

        LOG.info("Server OS: {} ({})", version.getOs(), version.getOsVersion());

        fireEvent(new MumbleEvents.ServerVersion(version));
    }

    /**
     * All mumble servers use MumbleProto.Ping for TCP pings.
     *
     * @param ping The ping data that was sent
     */
    private void onServerPongTcp(MumbleProto.Ping ping) {
        long now = System.nanoTime();
        long time = ping.getTimestamp();

        float delay = (now - time) / PING_MILLIS;

        float n = ++tcpPingPackets;
        tcpPingAverage += (delay - tcpPingAverage) / n;
        tcpPingDeviation = (float) Math.pow(Math.abs(delay - tcpPingAverage), 2);

        fireEvent(new MumbleEvents.ServerPongTcp(ping));
    }

    /**
     * If the mumble server has a v2 version, the ping message is a MumbleUDPProto.Ping
     * See: <a href="https://github.com/mumble-voip/mumble/pull/5837">FIX(client, server): Fix patch versions > 255</a>
     *
     * @param ping The ping data that was sent
     * @param udp
     */
    private void onServerPongUdpProtobuf(MumbleUDPProto.Ping ping, boolean udp) {
        updateUdpPing(ping.getTimestamp(), udp);
        fireEvent(new MumbleEvents.ServerPongUdpProtobuf(ping));
    }

    /**
     * If the mumble server only has a v1 version, the ping message is just a MumbleVarInt encoded timestamp
     * See: <a href="https://github.com/mumble-voip/mumble/pull/5837">FIX(client, server): Fix patch versions > 255</a>
     *
     * @param timestamp Timestamp the ping was sent
     * @param udp
     */
    private void onServerPongUdpLegacy(long timestamp, boolean udp) {
        fireEvent(new MumbleEvents.ServerPongUdpLegacy(updateUdpPing(timestamp, udp)));
    }

    private float updateUdpPing(long timestamp, boolean udp) {
        long now = System.nanoTime();

        float delay = (now - timestamp) / PING_MILLIS;

        float n = ++udpPingPackets;
        udpPingAverage += (delay - udpPingAverage) / n;
        udpPingDeviation = (float) Math.pow(Math.abs(delay - udpPingAverage), 2);

        if (udp && tcpUdpTunnel) {
            if (udpPingAccumulator >= UDP_TCP_FALLBACK) {
                // We suddenly got a response, after sending out pings with multiple missing responses
                LOG.warn("[UDP] Server is responding to UDP packets again, disabling TCP tunnel");
            }
            // Fallback to tunneling UDP data through TCP
            LOG.info("[UDP] Connective active");
            tcpUdpTunnel = false;
            fireEvent(new MumbleEvents.TcpTunnelActive(false));
        }

        udpPingAccumulator = 0;

        return delay;
    }

    private void onServerReject(MumbleProto.Reject reject) {
        fireEvent(new MumbleEvents.ServerReject(reject));
    }

    private void onServerSync(MumbleProto.ServerSync sync) {
        this.self = users.get(Integer.toUnsignedLong(sync.getSession()));
        this.synced = true;

        LOG.info("Fully synced with server");

        // Schedule TCP & UDP pings to keep connection alive
        scheduler.scheduleAtFixedRate(this::pingTcp, 0, PING_PERIOD_SECONDS, TimeUnit.SECONDS);
        if (crypto.isInitialized()) {
            scheduler.scheduleAtFixedRate(this::pingUdp, 0, PING_PERIOD_SECONDS, TimeUnit.SECONDS);
        }
        scheduler.scheduleAtFixedRate(this::mixAndPlayAudio, 0, 20, TimeUnit.MILLISECONDS);

        fireEvent(new MumbleEvents.ServerSync(this.self, sync));
    }

    private void mixAndPlayAudio() {
        short[] mixBuffer = new short[SAMPLES_PER_FRAME_TOTAL];

        for (MumbleUser user : getUsers()) {
            short[] userBuffer = new short[SAMPLES_PER_FRAME_TOTAL];
            int samples = user.popPcmAudio(userBuffer, SAMPLES_PER_FRAME_TOTAL);

            if (samples < SAMPLES_PER_FRAME_TOTAL) {
                continue;
            }

            for (int i = 0; i < samples; i++) {
                int mixed = mixBuffer[i] + userBuffer[i];
                mixBuffer[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            }
        }

        byte[] audioBytes = shortsToBytes(mixBuffer);
        audioOutput.write(audioBytes, 0, audioBytes.length);
    }

    /**
     * A channel is being removed, so remove it from childrenByParent's list of children
     *
     * @param child The child channel to be removed
     */
    private void removeChildFromParent(MumbleChannel child) {
        long parentId = child.getParentId();
        List<MumbleChannel> allChildren = childrenByParent.get(parentId);
        if (allChildren != null) {
            allChildren.remove(child);
            if (allChildren.isEmpty()) {
                childrenByParent.remove(parentId);
            }
        }
    }

    /**
     * Update our childrenByParent to maintain a list of children
     *
     * @param channel     Channel we are updating
     * @param newParentId Channels new parent ID
     */
    private void updateChannelParent(MumbleChannel channel, long newParentId) {
        long oldParentId = channel.getParentId();

        if (Objects.equals(oldParentId, newParentId)) {
            return;
        }

        removeChildFromParent(channel);

        childrenByParent.computeIfAbsent(newParentId, k -> new ArrayList<>()).add(channel);
    }

    private void onChannelRemove(MumbleProto.ChannelRemove channelRemove) {
        MumbleChannel channel = channels.remove(Integer.toUnsignedLong(channelRemove.getChannelId()));

        if (channel != null) {
            removeChildFromParent(channel);
        }

        fireEvent(new MumbleEvents.ChannelRemove(channel, channelRemove));
    }

    private void onChannelState(MumbleProto.ChannelState channelState) {
        long channelId = Integer.toUnsignedLong(channelState.getChannelId());

        // Get the MumbleChannel for this channel ID or create a new one
        MumbleChannel channel = channels.computeIfAbsent(channelId, key -> new MumbleChannel(this, channelId));

        if (channelState.hasParent()) {
            // The parent channel ID state is changing
            updateChannelParent(channel, channelState.getParent());
        }

        // Update our MumbleChannel with the current state
        channel.update(channelState);

        // Only send events after we are synced
        if (this.synced) fireEvent(new MumbleEvents.ChannelState(channel, channelState));
    }

    private void removeUserFromChannel(long channel, MumbleUser user) {
        List<MumbleUser> oldList = usersInChannel.get(channel);
        if (oldList != null) {
            oldList.remove(user);
            if (oldList.isEmpty()) {
                usersInChannel.remove(channel);
            }
        }
    }

    private void onUserRemove(MumbleProto.UserRemove userRemove) {
        MumbleUser user = users.remove(Integer.toUnsignedLong(userRemove.getSession()));

        long channel = user.getChannelId();

        removeUserFromChannel(channel, user);

        fireEvent(new MumbleEvents.UserRemove(user, userRemove));
    }

    private void onUserState(MumbleProto.UserState userState) {
        long session = userState.getSession();

        MumbleUser user = users.computeIfAbsent(session, key -> new MumbleUser(this, session));

        if (userState.hasChannelId()) {
            long oldChannel = user.getChannelId();
            long newChannel = Integer.toUnsignedLong(userState.getChannelId());

            if (oldChannel != newChannel) {
                removeUserFromChannel(oldChannel, user);
            }

            usersInChannel.computeIfAbsent(newChannel, k -> new ArrayList<>()).add(user);
        }

        if (this.synced && userState.hasChannelId()) {
            // We are fully synced and the user is changing channels
            long fromChannelId = user.getChannelId();
            long toChannelId = userState.getChannelId();

            if (!Objects.equals(fromChannelId, toChannelId)) {
                MumbleChannel from = getChannel(fromChannelId);
                MumbleChannel to = getChannel(toChannelId);
                // Custom event to signal that the user changed their channel
                fireEvent(new MumbleEvents.UserChangedChannel(user, from, to));
            }
        }

        // Update MumbleUser object with our current state
        user.update(userState);

        // Only send events after we are synced
        if (this.synced) fireEvent(new MumbleEvents.UserState(userState));
    }

    private void onBanList(MumbleProto.BanList banList) {
        fireEvent(new MumbleEvents.BanList(banList));
    }

    private void onTextMessage(MumbleProto.TextMessage textMessage) {
        fireEvent(new MumbleEvents.TextMessage(textMessage));
    }

    private void onPermissionDenied(MumbleProto.PermissionDenied permissionDenied) {
        fireEvent(new MumbleEvents.PermissionDenied(permissionDenied));
    }

    private void onAcl(MumbleProto.ACL acl) {
        fireEvent(new MumbleEvents.Acl(acl));
    }

    private void onQueryUsers(MumbleProto.QueryUsers queryUsers) {
        fireEvent(new MumbleEvents.QueryUsers(queryUsers));
    }

    private void onCryptSetup(MumbleProto.CryptSetup cryptSetup) {
        if (cryptSetup.hasKey() && cryptSetup.hasClientNonce() && cryptSetup.hasServerNonce()) {
            if (!crypto.setKey(cryptSetup.getKey().toByteArray(), cryptSetup.getClientNonce().toByteArray(),
                    cryptSetup.getServerNonce().toByteArray())) {
                LOG.error("Cipher resync failed: Invalid key/nonce from the server!");
            }
        } else if (cryptSetup.hasServerNonce()) {
            if (!crypto.setDecryptIV(cryptSetup.getServerNonce().toByteArray())) {
                LOG.error("Cipher resync failed: Invalid nonce from the server!");
            }
        } else {
            MumbleProto.CryptSetup.Builder crypt = MumbleProto.CryptSetup.newBuilder();
            crypt.setClientNonce(ByteString.copyFrom(crypto.getEncryptIV()));
            send(MumbleMessageType.CRYPT_SETUP, crypt.build());
            LOG.warn("Cipher disagreement: Renegotiating with server!");
        }

        if (crypto.isInitialized()) {
            LOG.info("Cipher setup: handshake complete");
        } else {
            LOG.warn("Cipher setup: handshake failed");
        }

        fireEvent(new MumbleEvents.CryptSetup(cryptSetup));
    }

    private void onContextActionModify(MumbleProto.ContextActionModify contextActionModify) {
        fireEvent(new MumbleEvents.ContextActionModify(contextActionModify));
    }

    private void onUserList(MumbleProto.UserList userList) {
        fireEvent(new MumbleEvents.UserList(userList));
    }

    private void onPermissionQuery(MumbleProto.PermissionQuery permissionQuery) {
        fireEvent(new MumbleEvents.PermissionQuery(permissionQuery));
    }

    private void onCodecVersion(MumbleProto.CodecVersion codecVersion) {
        fireEvent(new MumbleEvents.CodecVersion(codecVersion));
    }

    private void onUserStats(MumbleProto.UserStats userStats) {
        fireEvent(new MumbleEvents.UserStats(userStats));
    }

    private void onServerConfig(MumbleProto.ServerConfig serverConfig) {
        fireEvent(new MumbleEvents.ServerConfig(serverConfig));
    }

    private void onSuggestConfig(MumbleProto.SuggestConfig suggestConfig) {
        fireEvent(new MumbleEvents.SuggestConfig(suggestConfig));
    }

    private void onPluginDataTransmission(MumbleProto.PluginDataTransmission pluginDataTransmission) {
        fireEvent(new MumbleEvents.PluginDataTransmission(pluginDataTransmission));
    }

    private void onDisconnected(String reason) {
        fireEvent(new MumbleEvents.Disconnected(reason));
        scheduler.close();
    }

    public void connect() throws IOException, GeneralSecurityException {
        connectInternal(null);
    }

    public void connect(String pkcs12File) throws IOException, GeneralSecurityException {
        connectInternal(pkcs12File);
    }

    private void connectInternal(String pkcs12File) throws IOException, GeneralSecurityException {
        // Open selector and socket channel
        selector = Selector.open();
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(hostname, port));
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        // Setup SSL context and engine
        SSLContext sslContext = SSLContext.getInstance("TLS");

        KeyManager[] keyManagers = null;
        if (pkcs12File != null) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(pkcs12File)) {
                keyStore.load(in, new char[0]); // empty password
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);
            keyManagers = kmf.getKeyManagers();
        }

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        sslContext.init(keyManagers, trustAllCerts, new SecureRandom());

        sslEngine = sslContext.createSSLEngine(hostname, port);
        sslEngine.setUseClientMode(true);
        sslEngine.beginHandshake();

        // Allocate buffers
        SSLSession session = sslEngine.getSession();
        appInBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
        appOutBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
        netInBuffer = ByteBuffer.allocate(session.getPacketBufferSize());
        netOutBuffer = ByteBuffer.allocate(session.getPacketBufferSize());

        // Start in a new thread
        new Thread(this::processLoop, "MumbleClient-ProcessThread").start();
    }

    private void processLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                selector.select();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    try {
                        if (key.isConnectable()) {
                            finishConnect(key);
                        }

                        if (key.isReadable()) {
                            readFromChannel(key);
                        }

                        if (key.isWritable()) {
                            writeToChannel(key);
                        }
                    } catch (CancelledKeyException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Error reading from TCP socket", e);
            onDisconnected(ExceptionUtils.getRootCause(e).getLocalizedMessage());
        } finally {
            try {
                connected = false;
                selector.close();
                socketChannel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void finishConnect(SelectionKey key) throws IOException {
        if (socketChannel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            doHandshake();
        } else {
            // Connection failed
            key.cancel();
        }
    }

    private void doHandshake() throws IOException {
        SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();

        while (hsStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                hsStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

            switch (hsStatus) {
                case NEED_UNWRAP:
                    // Wait for data from server, handled in readFromChannel()
                    return;

                case NEED_WRAP:
                    netOutBuffer.clear();
                    SSLEngineResult wrapResult = sslEngine.wrap(ByteBuffer.allocate(0), netOutBuffer);
                    netOutBuffer.flip();
                    while (netOutBuffer.hasRemaining()) {
                        socketChannel.write(netOutBuffer);
                    }
                    hsStatus = wrapResult.getHandshakeStatus();
                    break;

                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    hsStatus = sslEngine.getHandshakeStatus();
                    break;

                default:
                    throw new IllegalStateException("Invalid Handshake Status: " + hsStatus);
            }
        }

        if (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
            connected = true;
            onConnectedTcp();
        }
    }

    private void readFromChannel(SelectionKey key) throws IOException {
        netInBuffer.clear();
        int bytesRead = socketChannel.read(netInBuffer);
        if (bytesRead == -1) {
            key.cancel();
            socketChannel.close();
            onDisconnected("Server closed the connection");
            return;
        }
        netInBuffer.flip();

        while (netInBuffer.hasRemaining()) {
            appInBuffer.clear();
            SSLEngineResult result = sslEngine.unwrap(netInBuffer, appInBuffer);
            switch (result.getStatus()) {
                case OK:
                    appInBuffer.flip();
                    if (!connected) {
                        doHandshake();
                    } else {
                        processMessage();
                    }
                    break;

                case BUFFER_UNDERFLOW:
                    // Need more data; exit and wait for more network input
                    return;

                case BUFFER_OVERFLOW:
                    appInBuffer = enlargeApplicationBuffer(appInBuffer);
                    break;

                case CLOSED:
                    key.cancel();
                    socketChannel.close();
                    return;
            }
        }
    }

    private void processMessage() {
        appInBuffer.order(ByteOrder.BIG_ENDIAN);
        while (appInBuffer.remaining() >= 6) { // Minimum header size
            appInBuffer.mark();

            // Read message type (unsigned short)
            int typeId = appInBuffer.getShort() & 0xFFFF;

            // Read message length (int)
            int length = appInBuffer.getInt();

            if (length < 0) {
                // Invalid length, maybe corrupted data - handle or disconnect
                LOG.error("Invalid message length: {}", length);
                onDisconnected("Invalid message length");
                return;
            }

            if (appInBuffer.remaining() < length) {
                // Not enough data yet for full message, reset position and wait for more
                appInBuffer.reset();
                break;
            }

            // Extract protobuf bytes for this message
            byte[] data = new byte[length];
            appInBuffer.get(data);

            // Convert typeId to MumbleMessageType enum if you have one
            MumbleMessageType messageType = MumbleMessageType.fromId(typeId);

            if (messageType == null) {
                LOG.error("Unknown message type: {}", typeId);
                continue; // Or handle unknown message
            }

            try {
                if (legacyConnection && messageType.equals(MumbleMessageType.UDP_TUNNEL)) {
                    handleUdpLegacyPacket(ByteBuffer.wrap(data), false);
                } else {
                    // Use protobuf parser for the message type
                    MessageLite message = parseProtobufMessage(messageType, data);

                    // Dispatch or handle the message as needed
                    handleMessage(messageType, message);
                }
            } catch (Exception e) {
                LOG.error("Failed to handle message of type {}: {}", messageType, e.getMessage());
            }
        }

        // Compact buffer to discard processed data and prepare for more reading
        appInBuffer.compact();
    }

    private void onUdpTunnel(ByteBuffer data, boolean udp) {
        if (legacyConnection) {
            handleUdpLegacyPacket(data, udp);
        } else {
            handleUdpProtobufPacket(data, udp);
        }
    }

    private void handleUdpLegacyPacket(ByteBuffer data, boolean udp) {
        byte header = data.get();
        byte type = (byte) ((header >> 5) & 0x7);

        switch (type) {
            case MumblePacketTypeLegacy.PING: {
                onServerPongUdpLegacy(MumbleVarInt.readVarIntLong(data), udp);
                break;
            }
            case MumblePacketTypeLegacy.OPUS:
            case MumblePacketTypeLegacy.SPEEX:
            case MumblePacketTypeLegacy.CELT_ALPHA:
            case MumblePacketTypeLegacy.CELT_BETA: {
                byte target = (byte) (header & 0x1F);
                handleLegacyAudio(target, type, data); // id is codec in this case
                break;
            }
            default:
                throw new IllegalStateException("Tried to process unhandled legacy UDP packet: " + type);
        }
    }

    private void handleUdpProtobufPacket(ByteBuffer data, boolean udp) {
        byte id = data.get();

        try {
            switch (id) {
                case MumblePacketTypeProtobuf.AUDIO: {
                    MumbleUDPProto.Audio audio = MumbleUDPProto.Audio.parseFrom(data);
                    handleProtobufAudio(audio);
                    break;
                }
                case MumblePacketTypeProtobuf.PING: {
                    MumbleUDPProto.Ping ping = MumbleUDPProto.Ping.parseFrom(data);
                    onServerPongUdpProtobuf(ping, udp);
                    break;
                }
                default:
                    throw new IllegalStateException("Tried to process unhandled protobuf UDP packet: " + id);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Unable to parse protobuf packet: " + id, e);
        }
    }

    /**
     * If the mumble server has a v2 version, the audio data comes in a protobuf message
     * See: <a href="https://github.com/mumble-voip/mumble/pull/5837">FIX(client, server): Fix patch versions > 255</a>
     *
     * @param audio Audio protobuf message
     */
    private void handleProtobufAudio(MumbleUDPProto.Audio audio) {
        long session = audio.getSenderSession();
        long sequence = audio.getFrameNumber();
        boolean speaking = audio.getIsTerminator();
    }

    /**
     * If the mumble server only has a v1 version, the audio data comes in as a raw packet
     * See: <a href="https://github.com/mumble-voip/mumble/pull/5837">FIX(client, server): Fix patch versions > 255</a>
     *
     * @param target Voice target the audio is for
     * @param codec  Audio codec of the audio data
     * @param data   The data we received
     */
    private void handleLegacyAudio(byte target, byte codec, ByteBuffer data) {
        long session = MumbleVarInt.readVarIntLong(data);
        long sequence = MumbleVarInt.readVarIntLong(data);

        MumbleUser user = getUser(session);

        boolean speaking = false;
        int payloadLen = 0;
        if (codec == MumblePacketTypeLegacy.SPEEX || codec == MumblePacketTypeLegacy.CELT_ALPHA || codec == MumblePacketTypeLegacy.CELT_BETA) {
            byte frameHeader = data.get();
            payloadLen = frameHeader & 0x7F;
            speaking = ((frameHeader & 0x80) == 0);

            byte[] payload = new byte[payloadLen];
            data.get(payload);
        } else if (codec == MumblePacketTypeLegacy.OPUS) {
            long frameHeader = MumbleVarInt.readVarIntLong(data);
            payloadLen = Math.toIntExact(frameHeader & 0x1FFF);
            speaking = ((frameHeader & 0x2000) == 0);

            byte[] payload = new byte[payloadLen];
            data.get(payload);

            int frameSize = opusDecoder.getNbSamples(payload);
            short[] pcm = new short[frameSize * CHANNELS];
            int decodedSamples = opusDecoder.decode(payload, pcm, frameSize);

            user.pushPcmAudio(pcm, decodedSamples);
        }
    }

    public static byte[] shortsToBytes(short[] input) {
        byte[] output = new byte[input.length * 2];
        for (int i = 0; i < input.length; i++) {
            output[i * 2] = (byte) (input[i] & 0xFF);         // low byte
            output[i * 2 + 1] = (byte) ((input[i] >> 8) & 0xFF);   // high byte
        }
        return output;
    }

    private void handleMessage(MumbleMessageType messageType, MessageLite message) {
        switch (messageType) {
            case VERSION -> {
                if (message instanceof MumbleProto.Version version) {
                    onServerVersion(version);
                }
            }
            case UDP_TUNNEL -> {
                if (message instanceof MumbleProto.UDPTunnel tunnel) {
                    onUdpTunnel(tunnel.getPacket().asReadOnlyByteBuffer(), false);
                }
            }
            case PING -> {
                if (message instanceof MumbleProto.Ping ping) {
                    onServerPongTcp(ping);
                }
            }
            case SERVER_REJECT -> {
                if (message instanceof MumbleProto.Reject reject) {
                    onServerReject(reject);
                }
            }
            case SERVER_SYNC -> {
                if (message instanceof MumbleProto.ServerSync sync) {
                    onServerSync(sync);
                }
            }
            case CHANNEL_REMOVE -> {
                if (message instanceof MumbleProto.ChannelRemove channelRemove) {
                    onChannelRemove(channelRemove);
                }
            }
            case CHANNEL_STATE -> {
                if (message instanceof MumbleProto.ChannelState channelState) {
                    onChannelState(channelState);
                }
            }
            case USER_REMOVE -> {
                if (message instanceof MumbleProto.UserRemove userRemove) {
                    onUserRemove(userRemove);
                }
            }
            case USER_STATE -> {
                if (message instanceof MumbleProto.UserState userState) {
                    onUserState(userState);
                }
            }
            case BAN_LIST -> {
                if (message instanceof MumbleProto.BanList banList) {
                    onBanList(banList);
                }
            }
            case TEXT_MESSAGE -> {
                if (message instanceof MumbleProto.TextMessage textMessage) {
                    onTextMessage(textMessage);
                }
            }
            case PERMISSION_DENIED -> {
                if (message instanceof MumbleProto.PermissionDenied permissionDenied) {
                    onPermissionDenied(permissionDenied);
                }
            }
            case ACL -> {
                if (message instanceof MumbleProto.ACL acl) {
                    onAcl(acl);
                }
            }
            case QUERY_USERS -> {
                if (message instanceof MumbleProto.QueryUsers queryUsers) {
                    onQueryUsers(queryUsers);
                }
            }
            case CRYPT_SETUP -> {
                if (message instanceof MumbleProto.CryptSetup cryptSetup) {
                    onCryptSetup(cryptSetup);
                }
            }
            case CONTEXT_ACTION_MOD -> {
                if (message instanceof MumbleProto.ContextActionModify contextActionModify) {
                    onContextActionModify(contextActionModify);
                }
            }
            case USER_LIST -> {
                if (message instanceof MumbleProto.UserList userList) {
                    onUserList(userList);
                }
            }
            case PERMISSION_QUERY -> {
                if (message instanceof MumbleProto.PermissionQuery permissionQuery) {
                    onPermissionQuery(permissionQuery);
                }
            }
            case CODEC_VERSION -> {
                if (message instanceof MumbleProto.CodecVersion codecVersion) {
                    onCodecVersion(codecVersion);
                }
            }
            case USER_STATS -> {
                if (message instanceof MumbleProto.UserStats userStats) {
                    onUserStats(userStats);
                }
            }
            case SERVER_CONFIG -> {
                if (message instanceof MumbleProto.ServerConfig serverConfig) {
                    onServerConfig(serverConfig);
                }
            }
            case SUGGEST_CONFIG -> {
                if (message instanceof MumbleProto.SuggestConfig suggestConfig) {
                    onSuggestConfig(suggestConfig);
                }
            }
            case PLUGIN_DATA_TRANSMISSION -> {
                if (message instanceof MumbleProto.PluginDataTransmission pluginDataTransmission) {
                    onPluginDataTransmission(pluginDataTransmission);
                }
            }
        }

        String protoName;
        if (message instanceof Message) {
            Descriptors.Descriptor descriptor = ((Message) message).getDescriptorForType();
            protoName = descriptor.getName();
        } else {
            protoName = message.getClass().getSimpleName();
        }

        LOG.debug("Received {}: {}", protoName, toHex(message.toByteArray()));
    }

    private MessageLite parseProtobufMessage(MumbleMessageType type, byte[] bytes) throws Exception {
        return switch (type) {
            case VERSION -> MumbleProto.Version.parseFrom(bytes);
            case UDP_TUNNEL -> MumbleProto.UDPTunnel.parseFrom(bytes);
            case AUTHENTICATE -> MumbleProto.Authenticate.parseFrom(bytes);
            case PING -> MumbleProto.Ping.parseFrom(bytes);
            case SERVER_REJECT -> MumbleProto.Reject.parseFrom(bytes);
            case SERVER_SYNC -> MumbleProto.ServerSync.parseFrom(bytes);
            case CHANNEL_REMOVE -> MumbleProto.ChannelRemove.parseFrom(bytes);
            case CHANNEL_STATE -> MumbleProto.ChannelState.parseFrom(bytes);
            case USER_REMOVE -> MumbleProto.UserRemove.parseFrom(bytes);
            case USER_STATE -> MumbleProto.UserState.parseFrom(bytes);
            case BAN_LIST -> MumbleProto.BanList.parseFrom(bytes);
            case TEXT_MESSAGE -> MumbleProto.TextMessage.parseFrom(bytes);
            case PERMISSION_DENIED -> MumbleProto.PermissionDenied.parseFrom(bytes);
            case ACL -> MumbleProto.ACL.parseFrom(bytes);
            case QUERY_USERS -> MumbleProto.QueryUsers.parseFrom(bytes);
            case CRYPT_SETUP -> MumbleProto.CryptSetup.parseFrom(bytes);
            case CONTEXT_ACTION_MOD -> MumbleProto.ContextActionModify.parseFrom(bytes);
            case CONTEXT_ACTION -> MumbleProto.ContextAction.parseFrom(bytes);
            case USER_LIST -> MumbleProto.UserList.parseFrom(bytes);
            case VOICE_TARGET -> MumbleProto.VoiceTarget.parseFrom(bytes);
            case PERMISSION_QUERY -> MumbleProto.PermissionQuery.parseFrom(bytes);
            case CODEC_VERSION -> MumbleProto.CodecVersion.parseFrom(bytes);
            case USER_STATS -> MumbleProto.UserStats.parseFrom(bytes);
            case REQUEST_BLOB -> MumbleProto.RequestBlob.parseFrom(bytes);
            case SERVER_CONFIG -> MumbleProto.ServerConfig.parseFrom(bytes);
            case SUGGEST_CONFIG -> MumbleProto.SuggestConfig.parseFrom(bytes);
            case PLUGIN_DATA_TRANSMISSION -> MumbleProto.PluginDataTransmission.parseFrom(bytes);
        };
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String toHex(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        copy.rewind();
        StringBuilder sb = new StringBuilder();
        while (copy.hasRemaining()) {
            sb.append(String.format("%02X", copy.get()));
        }
        return sb.toString().trim();
    }

    private void writeToChannel(SelectionKey key) throws IOException {
        if (!key.isValid()) {
            LOG.error("writeToChannel: Key is invalid");
            return;
        }

        // Flush leftover encrypted data
        while (netOutBuffer.hasRemaining()) {
            int written = socketChannel.write(netOutBuffer);
            if (written == 0) break;
        }

        if (netOutBuffer.hasRemaining()) {
            // Not fully written, keep OP_WRITE interest
            return;
        }

        ByteBuffer nextPlaintext;
        synchronized (this) {
            nextPlaintext = outboundPlaintextQueue.poll();
        }
        if (nextPlaintext == null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            return;
        }

        netOutBuffer.clear();
        sslEngine.wrap(nextPlaintext, netOutBuffer);
        netOutBuffer.flip();

        // Write encrypted data fully as above
        while (netOutBuffer.hasRemaining()) {
            int written = socketChannel.write(netOutBuffer);
            if (written == 0) break;
        }

        if (netOutBuffer.hasRemaining()) {
            // Partial write, keep OP_WRITE interest
            return;
        }

        // If plaintext not fully consumed, requeue
        if (nextPlaintext.hasRemaining()) {
            synchronized (this) {
                outboundPlaintextQueue.add(nextPlaintext);
            }
        }

        // Recursively try to send more
        writeToChannel(key);
    }

    private ByteBuffer enlargeApplicationBuffer(ByteBuffer buffer) {
        SSLSession session = sslEngine.getSession();
        ByteBuffer newBuffer = ByteBuffer.allocate(session.getApplicationBufferSize() + buffer.position());
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    public synchronized void send(MumbleMessageType type, MessageLite message) {
        if (!connected) {
            LOG.error("send() called before handshake finished!");
            return; // Or throw or queue for later, depending on your design
        }

        byte[] protobufBytes = message.toByteArray();
        ByteBuffer framed = frameMessage(type, protobufBytes);

        String protoName;
        if (message instanceof Message) {
            Descriptors.Descriptor descriptor = ((Message) message).getDescriptorForType();
            protoName = descriptor.getName();
        } else {
            protoName = message.getClass().getSimpleName();
        }

        LOG.debug("Sending {}: {}", protoName, toHex(framed));

        outboundPlaintextQueue.offer(framed);
        enableWriteInterest();
    }

    private void enableWriteInterest() {
        SelectionKey key = socketChannel.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }

    private static ByteBuffer frameMessage(MumbleMessageType type, byte[] protobufBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + protobufBytes.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) type.getId());
        buffer.putInt(protobufBytes.length);
        buffer.put(protobufBytes);
        buffer.flip();
        return buffer;
    }

    public void authenticate(String username, String... tokens) {
        authenticate(username, null, tokens);
    }

    public void authenticate(String username, String password, String... tokens) {
        authenticate(username, password, MumbleClientType.NORMAL, tokens);
    }

    public void authenticate(String username, String password, MumbleClientType type, String... tokens) {
        MumbleProto.Authenticate.Builder auth = MumbleProto.Authenticate.newBuilder();
        auth.setUsername(username);
        if (password != null) {
            auth.setPassword(password);
        }
        auth.setClientType(type.getTypeId());
        auth.setOpus(true);
        auth.addAllTokens(List.of(tokens));
        send(MumbleMessageType.AUTHENTICATE, auth.build());
    }

    private void pingTcp() {
        MumbleProto.Ping.Builder ping = MumbleProto.Ping.newBuilder();
        ping.setTimestamp(System.nanoTime());
        ping.setGood(crypto.getGood());
        ping.setLate(crypto.getLate());
        ping.setLost(crypto.getLost());
        ping.setResync(crypto.getResync());
        ping.setTcpPackets(tcpPingPackets);
        ping.setTcpPingAvg(tcpPingAverage);
        ping.setTcpPingVar(tcpPingDeviation);
        ping.setUdpPackets(udpPingPackets);
        ping.setUdpPingAvg(udpPingAverage);
        ping.setUdpPingVar(udpPingDeviation);
        send(MumbleMessageType.PING, ping.build());
    }

    private void pingUdp() {
        if (legacyConnection) {
            ByteBuffer packet = ByteBuffer.allocate(UDP_BUFFER_MAX);
            packet.put((byte) (MumblePacketTypeLegacy.PING << 5));
            MumbleVarInt.writeVarInt(packet, System.nanoTime());
            packet.flip();
            try {
                udpConnection.send(crypto.encrypt(packet));
            } catch (Exception e) {
                LOG.error("Unable to encrypt or send UDP packet", e);
            }
        } else {
            MumbleUDPProto.Ping.Builder ping = MumbleUDPProto.Ping.newBuilder();
            ping.setTimestamp(System.nanoTime());
            try {
                udpConnection.send(crypto.encrypt(ping.build().toByteArray()));
            } catch (Exception e) {
                LOG.error("Unable to encrypt or send UDP packet", e);
            }
        }
        if (udpPingAccumulator >= UDP_TCP_FALLBACK && !tcpUdpTunnel) {
            // We didn't get a response from a ping 3 times in a row
            LOG.warn("[UDP] Server no longer responding to UDP pings, falling back to TCP..");
            tcpUdpTunnel = true;
            fireEvent(new MumbleEvents.TcpTunnelActive(true));
        }
        udpPingAccumulator++;
    }

    private void sendVersion() {
        MumbleProto.Version.Builder version = MumbleProto.Version.newBuilder();
        version.setOs(System.getProperty("os.name"));
        version.setOsVersion(System.getProperty("os.version"));
        version.setRelease("Grumble 1.0");
        version.setVersionV1(MUMBLE_VERSION_v1);
        version.setVersionV2(MUMBLE_VERSION_V2);
        send(MumbleMessageType.VERSION, version.build());
    }

    public List<MumbleUser> getUsers() {
        return List.copyOf(users.values());
    }

    public MumbleUser getUser(long session) {
        return users.get(session);
    }

    public List<MumbleChannel> getChannels() {
        return List.copyOf(channels.values());
    }

    public MumbleChannel getChannel(long channelId) {
        return channels.get(channelId);
    }

    public List<MumbleUser> getChannelUsers(MumbleChannel channel) {
        return getChannelUsers(channel.getChannelId());
    }

    public List<MumbleUser> getChannelUsers(long channelId) {
        List<MumbleUser> list = usersInChannel.get(channelId);
        return (list != null) ? List.copyOf(list) : Collections.emptyList();
    }


    /**
     * Returns a list of all MumbleChannel children for a given MumbleChannel
     *
     * @param channel MumbleChannel we want the list of children for
     * @return List of all MumbleChannel children
     */
    public List<MumbleChannel> getChildren(MumbleChannel channel) {
        return getChildren(channel.getChannelId());
    }

    /**
     * Returns a list of all MumbleChannel children for a given channelId
     *
     * @param channelId Channel ID we want the list of children for
     * @return List of all MumbleChannel children
     */
    public List<MumbleChannel> getChildren(long channelId) {
        return childrenByParent.get(channelId);
    }

    /**
     * Get the MumbleUser object that represents ourselves
     *
     * @return My MumbleUser object
     */
    public MumbleUser getSelf() {
        return this.self;
    }
}
