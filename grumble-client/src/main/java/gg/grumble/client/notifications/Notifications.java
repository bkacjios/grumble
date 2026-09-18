package gg.grumble.client.notifications;

public interface Notifications {
    void show(String title, String message, int timeoutMs);
}
