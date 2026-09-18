package gg.grumble.core.audio.output;

import javax.sound.sampled.*;

import static gg.grumble.core.enums.MumbleAudioConfig.CHANNELS;
import static gg.grumble.core.enums.MumbleAudioConfig.SAMPLE_RATE;

@SuppressWarnings("unused")
public class SourceDataLineOutputDevice implements AudioOutputDevice {
    // AudioOutput feeds this line a 20ms chunk every 20ms on a fixed-rate thread. Leaving
    // the mixer's default (often very small) internal buffer means any brief scheduling
    // hiccup on that thread underruns the line and clicks/stutters, regardless of how
    // clean the incoming network audio is. Give it real headroom to absorb that jitter.
    private static final int BUFFER_MILLIS = 200;

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
        int bufferSizeBytes = format.getFrameSize() * (SAMPLE_RATE * BUFFER_MILLIS / 1000);
        audioLine.open(format, bufferSizeBytes);
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
            dB = Math.clamp(dB, min, max);
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
        return Math.clamp(linear, 0.0f, 1.0f);
    }

    @Override
    public void close() {
        audioLine.stop();
        audioLine.flush();
        audioLine.close();
    }
}
