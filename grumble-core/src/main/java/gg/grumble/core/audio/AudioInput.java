package gg.grumble.core.audio;

import gg.grumble.core.audio.input.AudioInputDevice;
import gg.grumble.core.audio.input.NullAudioInputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static gg.grumble.core.enums.MumbleAudioConfig.PLAYBACK_DURATION_MS;

public class AudioInput {
    private static final Logger LOG = LoggerFactory.getLogger(AudioInput.class);

    private Thread audioThread;
    private volatile boolean running;
    private long intervalNanos;

    private final Consumer<byte[]> task;
    private AudioInputDevice audioDevice;
    private volatile AudioInputDevice pendingDevice;

    private float volume = 0.5f;

    /**
     * @param onAudioOut a Consumer that will accept the output PCM data
     */
    public AudioInput(Consumer<byte[]> onAudioOut) {
        this.task = onAudioOut;
        this.audioDevice = new NullAudioInputDevice();
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(PLAYBACK_DURATION_MS);
    }

    public void setFrameDurationMillis(int millis) {
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(millis);
    }

    public int getFrameDurationMillis() {
        return (int) TimeUnit.NANOSECONDS.toMillis(intervalNanos);
    }

    /**
     * Starts the engine using the device provided in the constructor.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        audioThread = new Thread(this::runLoop, "input");
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

    /**
     * Requests a swap to a new AudioOutput device.
     * If engine is running, swap happens on the audio thread at interval boundary.
     * If not running yet, simply replaces the device.
     */
    public synchronized void switchInputDevice(AudioInputDevice newDevice) {
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

            // read microphone for the given interval and accept it
            try {
                task.accept(audioDevice.read(getFrameDurationMillis()));
            } catch (Exception e) {
                LOG.error("Error in audio input handler", e);
            }

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
