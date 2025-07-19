package gg.grumble.core.audio.input;

public interface AudioInputDevice {
    void start();
    void stop();
    byte[] read(int frameSizeMillis);
    void setVolume(float volume);
    float getVolume();
    void close();
}
