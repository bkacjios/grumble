package gg.grumble.core.audio.output;

public interface AudioOutputDevice {
    void start();
    void stop();
    void write(byte[] pcm, int offset, int length);
    void setVolume(float volume);
    float getVolume();
    void close();
}