package gg.grumble.core.audio.output;

public class NullAudioOutputDevice implements AudioOutputDevice {
    public NullAudioOutputDevice() {
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
    }

    public float getVolume() {
        return 0;
    }

    @Override
    public void close() {
    }
}
