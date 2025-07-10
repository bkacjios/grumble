package gg.grumble.client.config;

import java.util.Collections;
import java.util.List;

public class ApplicationConfig {
    private float volume = 0.5f;
    private ServerConfig lastConnectedServer = null;
    private List<ServerConfig> favoriteServerList = Collections.emptyList();

    public float getVolume() { return volume; }
    public void setVolume(float volume) { this.volume = volume; }

    public ServerConfig getLastConnectedServer() {
        return lastConnectedServer;
    }

    public void setLastConnectedServer(ServerConfig lastConnectedServer) {
        this.lastConnectedServer = lastConnectedServer;
    }

    public List<ServerConfig> getFavoriteServerList() {
        return favoriteServerList;
    }

    public void setFavoriteServerList(List<ServerConfig> favoriteServerList) {
        this.favoriteServerList = favoriteServerList;
    }
}
