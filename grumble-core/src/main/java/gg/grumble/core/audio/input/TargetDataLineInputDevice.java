package gg.grumble.core.audio.input;

import gg.grumble.core.audio.AudioInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;

import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLE_RATE;

public class TargetDataLineInputDevice implements AudioInputDevice {
    private static final Logger LOG = LoggerFactory.getLogger(TargetDataLineInputDevice.class);

    private final TargetDataLine audioLine;

    public TargetDataLineInputDevice() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                SAMPLE_RATE,        // Sample rate
                16,                 // Sample size in bits
                1,                  // Channels (mono)
                true,               // Signed
                false               // Little-endian
        );
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        audioLine = (TargetDataLine) AudioSystem.getLine(info);
        audioLine.open(format);
    }

    @Override
    public void start() {
        audioLine.start();
        audioLine.flush();
    }

    @Override
    public void stop() {
        audioLine.stop();
    }

    @Override
    public byte[] read(int frameSizeMillis) {
        AudioFormat fmt = audioLine.getFormat();
        int bytesPerFrame = fmt.getFrameSize();                     // 2 bytes/sample x 1 channel = 2
        float framesPerMs = fmt.getFrameRate() / 1000f;             // 48000 Hz → 48 frames/ms
        int numFrames = Math.round(framesPerMs * frameSizeMillis);  // 48 x 20 ms = 960 frames
        int bufferLen = numFrames * bytesPerFrame;                  // 960 x 2 = 1920 bytes

        byte[] buffer = new byte[bufferLen];
        int offset = 0;
        while (offset < bufferLen) {
            int read = audioLine.read(buffer, offset, bufferLen - offset);
            if (read < 0) {
                throw new RuntimeException("End of stream reached");
            }
            offset += read;
        }

        return buffer;
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

    }
}
