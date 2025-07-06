package gg.grumble.core.audio;

public class NullAudioOutput implements AudioOutput {
    private float volume = 0.5f;

    public NullAudioOutput() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void write(byte[] pcm, int offset, int length) {
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
    }

    public float getVolume() {
        return volume;
    }

    @Override
    public void close() {
    }
}
