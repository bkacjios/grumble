package gg.grumble.core.client;

public interface MumbleEventListener<T> {
    void onEvent(T event);
}