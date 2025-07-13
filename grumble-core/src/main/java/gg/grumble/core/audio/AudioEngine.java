package gg.grumble.core.audio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static gg.grumble.core.enums.MumbleAudioConfig.PLAYBACK_DURATION_MS;

/**
 * Drives audio mixing and playback at a fixed interval on its own thread.
 * Accepts a Runnable that performs mixing and writes to audioOutput.
 * Allows hot-swapping the AudioOutput on the audio thread without restarting the thread.
 */
public class AudioEngine {
    private Thread audioThread;
    private volatile boolean running;
    private final long intervalNanos;

    private final Runnable task;
    private AudioOutput audioOutput;
    private volatile AudioOutput pendingDevice;

    private float volume = 0.5f;

    /**
     * @param task a Runnable that mixes audio and writes to audioOutput
     * @param initialDevice the AudioOutput to use when engine starts
     */
    public AudioEngine(Runnable task, AudioOutput initialDevice) {
        this.task = task;
        this.audioOutput = initialDevice;
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(PLAYBACK_DURATION_MS);
    }

    /**
     * Starts the engine using the device provided in the constructor.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        audioThread = new Thread(this::runLoop, "audio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public float getVolume() {
        return volume;
    }

    public void write(byte[] pcm, int offset, int length) {
        audioOutput.write(pcm, offset, length);
    }

    /**
     * Requests a swap to a new AudioOutput device.
     * If engine is running, swap happens on the audio thread at interval boundary.
     * If not running yet, simply replaces the device.
     */
    public synchronized void switchDevice(AudioOutput newDevice) {
        if (!running) {
            this.audioOutput = newDevice;
        } else {
            this.pendingDevice = newDevice;
        }
    }

    /**
     * Main fixed-rate loop: perform device swaps, execute task each interval, then cleanup.
     */
    private void runLoop() {
        // Initialize playback on this thread
        audioOutput.start();
        audioOutput.setVolume(volume);
        long nextTime = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long sleepNanos = nextTime - now;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            // Handle pending device swap if requested
            if (pendingDevice != null) {
                audioOutput.stop();
                audioOutput.close();
                audioOutput = pendingDevice;
                pendingDevice = null;
                audioOutput.start();
                audioOutput.setVolume(volume);
            }

            // perform mixing and playback
            task.run();

            nextTime += intervalNanos;
            // catch up if we're more than one interval behind
            if (System.nanoTime() - nextTime > intervalNanos) {
                nextTime = System.nanoTime();
            }
        }

        // cleanup device at shutdown
        audioOutput.stop();
        audioOutput.close();
    }

    /**
     * Stops the audio engine and releases the device.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        audioThread.interrupt();
        try {
            audioThread.join();
        } catch (InterruptedException ignored) {
        }
        audioOutput.stop();
        audioOutput.close();
    }
}
