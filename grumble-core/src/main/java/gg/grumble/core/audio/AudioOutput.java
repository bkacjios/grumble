package gg.grumble.core.audio;

public interface AudioOutput {
    void start();
    void stop();
    void write(byte[] pcm, int offset, int length);
    void setVolume(float volume);
    float getVolume();
    void close();
}