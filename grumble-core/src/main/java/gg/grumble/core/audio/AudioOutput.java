package gg.grumble.core.audio;

import gg.grumble.core.audio.output.AudioOutputDevice;
import gg.grumble.core.audio.output.NullAudioOutputDevice;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static gg.grumble.core.enums.MumbleAudioConfig.PLAYBACK_DURATION_MS;

/**
 * Drives audio mixing and playback at a fixed interval on its own thread.
 * Accepts a Runnable that performs mixing and writes to audioOutput.
 * Allows hot-swapping the AudioOutput on the audio thread without restarting the thread.
 */
public class AudioOutput {
    private Thread audioThread;
    private volatile boolean running;
    private final long intervalNanos;

    private final Runnable task;
    private AudioOutputDevice audioDevice;
    private volatile AudioOutputDevice pendingDevice;

    private float volume = 0.5f;

    /**
     * @param task a Runnable that mixes audio and writes to audioOutput
     */
    public AudioOutput(Runnable task) {
        this.task = task;
        this.audioDevice = new NullAudioOutputDevice();
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(PLAYBACK_DURATION_MS);
    }

    /**
     * Starts the engine using the device provided in the constructor.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        audioThread = new Thread(this::runLoop, "output");
        audioThread.setDaemon(true);
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (pendingDevice != null) {
            pendingDevice.setVolume(volume);
        } else if (audioDevice != null) {
            audioDevice.setVolume(volume);
        }
    }

    public float getVolume() {
        return volume;
    }

    public void write(byte[] pcm, int offset, int length) {
        audioDevice.write(pcm, offset, length);
    }

    /**
     * Requests a swap to a new AudioOutput device.
     * If engine is running, swap happens on the audio thread at interval boundary.
     * If not running yet, simply replaces the device.
     */
    public synchronized void switchOutputDevice(AudioOutputDevice newDevice) {
        if (!running) {
            this.audioDevice = newDevice;
        } else {
            this.pendingDevice = newDevice;
        }
    }

    /**
     * Main fixed-rate loop: perform device swaps, execute task each interval, then cleanup.
     */
    private void runLoop() {
        // Initialize playback on this thread
        audioDevice.setVolume(volume);
        audioDevice.start();
        long nextTime = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long sleepNanos = nextTime - now;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            // Handle pending device swap if requested
            if (pendingDevice != null) {
                audioDevice.stop();
                audioDevice.close();
                audioDevice = pendingDevice;
                pendingDevice = null;
                audioDevice.setVolume(volume);
                audioDevice.start();
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
        audioDevice.stop();
        audioDevice.close();
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
        audioDevice.stop();
        audioDevice.close();
    }
}
