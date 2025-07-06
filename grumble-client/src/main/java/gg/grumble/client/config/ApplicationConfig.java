package gg.grumble.client.config;

public class ApplicationConfig {
    private String username = "";
    private int volume = 75;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getVolume() { return volume; }
    public void setVolume(int volume) { this.volume = volume; }
}
