package gg.grumble.core.audio.input;

import java.util.HashMap;
import java.util.Map;

import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLE_RATE;

public class NullAudioInputDevice implements AudioInputDevice {
    private final Map<Integer, byte[]> silenceCache = new HashMap<>();

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public byte[] read(int frameSizeMillis) {
        int bytesPerFrame = 2;
        float framesPerMs = SAMPLE_RATE / 1000f;
        int numFrames = Math.round(framesPerMs * frameSizeMillis);
        int bufferLen = numFrames * bytesPerFrame;
        try {
            Thread.sleep(frameSizeMillis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return silenceCache.computeIfAbsent(frameSizeMillis, size -> new byte[bufferLen]);
    }

    @Override
    public void setVolume(float volume) {

    }

    @Override
    public float getVolume() {
        return 0;
    }

    @Override
    public void close() {
        silenceCache.clear();
    }
}
