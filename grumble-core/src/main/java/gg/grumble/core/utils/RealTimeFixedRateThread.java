package gg.grumble.core.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class RealTimeFixedRateThread extends Thread {
    private final Runnable task;
    private final long intervalNanos;
    private volatile boolean running = true;

    public RealTimeFixedRateThread(Runnable task, long interval, TimeUnit unit) {
        super("RealTimeFixedRateThread");
        this.task = task;
        this.intervalNanos = unit.toNanos(interval);
        setDaemon(true);
    }

    @Override
    public void run() {
        long nextTime = System.nanoTime();

        while (running && !isInterrupted()) {
            long now = System.nanoTime();
            long sleepNanos = nextTime - now;

            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            task.run();

            nextTime += intervalNanos;

            if (System.nanoTime() - nextTime > intervalNanos) {
                nextTime = System.nanoTime();
            }
        }
    }

    public void shutdown() {
        running = false;
        interrupt();
    }
}
