package gg.grumble.client.utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Pings Mumble servers using the raw (unauthenticated) UDP ping packet murmur replies to
 * with its current/max user count, ahead of actually connecting.
 * <p>
 * Requests are run on a small thread pool, ordered so favorite servers are pinged before
 * LAN servers, which are pinged before public servers, regardless of what order they were
 * queued in.
 */
public class MumbleServerPingQueue {

    private static final int THREAD_COUNT = 8;
    private static final int SOCKET_TIMEOUT_MS = 1500;

    public enum Priority {
        FAVORITE, LAN, PUBLIC
    }

    public record PingResult(int users, int maxUsers, long pingMillis) {
    }

    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor executor;

    public MumbleServerPingQueue() {
        this.executor = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT,
                0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                r -> {
                    Thread thread = new Thread(r, "mumble-ping");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public void ping(String host, int port, Priority priority, Consumer<PingResult> onSuccess, Consumer<Throwable> onFailure) {
        executor.execute(new PingTask(priority, sequence.getAndIncrement(), host, port, onSuccess, onFailure));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class PingTask implements Runnable, Comparable<PingTask> {
        private final Priority priority;
        private final long sequence;
        private final String host;
        private final int port;
        private final Consumer<PingResult> onSuccess;
        private final Consumer<Throwable> onFailure;

        private PingTask(Priority priority, long sequence, String host, int port,
                          Consumer<PingResult> onSuccess, Consumer<Throwable> onFailure) {
            this.priority = priority;
            this.sequence = sequence;
            this.host = host;
            this.port = port;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }

        @Override
        public void run() {
            try {
                onSuccess.accept(ping());
            } catch (IOException e) {
                onFailure.accept(e);
            }
        }

        /**
         * Sends murmur's out-of-band server info request: a 4 byte zero marker followed by an
         * 8 byte request id the server echoes back, alongside its current/max user count.
         */
        private PingResult ping() throws IOException {
            long requestId = ThreadLocalRandom.current().nextLong();

            ByteBuffer request = ByteBuffer.allocate(12);
            request.putInt(0);
            request.putLong(requestId);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                DatagramPacket requestPacket = new DatagramPacket(
                        request.array(), request.array().length, InetAddress.getByName(host), port);

                long start = System.nanoTime();
                socket.send(requestPacket);

                byte[] responseBytes = new byte[24];
                DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length);
                socket.receive(responsePacket);
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

                ByteBuffer response = ByteBuffer.wrap(responseBytes, 0, responsePacket.getLength());
                if (response.remaining() < 24) {
                    throw new IOException("Malformed ping response from " + host + ":" + port);
                }

                response.getInt(); // version, unused
                long responseId = response.getLong();
                if (responseId != requestId) {
                    throw new IOException("Ping response id mismatch from " + host + ":" + port);
                }

                int users = response.getInt();
                int maxUsers = response.getInt();

                return new PingResult(users, maxUsers, elapsedMillis);
            }
        }

        @Override
        public int compareTo(PingTask other) {
            int cmp = priority.compareTo(other.priority);
            if (cmp != 0) return cmp;
            return Long.compare(sequence, other.sequence);
        }
    }
}
