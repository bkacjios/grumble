package gg.grumble.core.audio.output;

import javax.sound.sampled.*;

import static gg.grumble.core.enums.MumbleAudioConfig.CHANNELS;
import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLE_RATE;

@SuppressWarnings("unused")
public class SourceDataLineOutputDevice implements AudioOutputDevice {
    private final SourceDataLine audioLine;

    public SourceDataLineOutputDevice() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                SAMPLE_RATE,        // Sample rate
                16,                 // Sample size in bits
                CHANNELS,           // Channels (stereo)
                true,               // Signed
                false               // Little-endian
        );
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        audioLine = (SourceDataLine) AudioSystem.getLine(info);
        audioLine.open(format);
    }

    @Override
    public void start() {
        audioLine.start();
    }

    @Override
    public void stop() {
        audioLine.stop();
    }

    @Override
    public void write(byte[] pcm, int offset, int length) {
        audioLine.write(pcm, offset, length);
    }

    @Override
    public void setVolume(float volume) {
        if (volume < 0f || volume > 1f) {
            throw new IllegalArgumentException("Volume must be between 0.0 and 1.0");
        }

        if (!audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            throw new IllegalStateException("Audio device does not support gain control");
        }

        FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
        float min = gainControl.getMinimum(); // Usually negative dB, like -80.0
        float max = gainControl.getMaximum(); // Usually 0.0 dB

        // Avoid log(0); treat 0 volume as min
        float dB;
        if (volume == 0f) {
            dB = min;
        } else {
            // Logarithmic volume scaling: perceptually linear
            dB = (float) (Math.log10(volume) * 20.0);
            // Clamp to range
            dB = Math.max(min, Math.min(dB, max));
        }

        gainControl.setValue(dB);
    }

    @Override
    public float getVolume() {
        if (!audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            throw new IllegalStateException("Audio device does not support gain control");
        }

        FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
        float dB = gainControl.getValue(); // Current dB
        float min = gainControl.getMinimum(); // Usually -80.0

        // Handle edge case
        if (dB <= min) return 0.0f;

        // Convert dB to linear volume
        float linear = (float) Math.pow(10.0, dB / 20.0);

        // Clamp just in case
        return Math.max(0.0f, Math.min(1.0f, linear));
    }

    @Override
    public void close() {
        audioLine.stop();
        audioLine.flush();
        audioLine.close();
    }
}
