package gg.grumble.client.utils;

import java.io.File;
import java.nio.file.Path;

public class FileUtils {
    public static Path getAppConfigPath(String appName, String fileName) {
        String os = System.getProperty("os.name").toLowerCase();
        String configDir;
        if (os.contains("win")) {
            configDir = System.getenv("APPDATA");
            if (configDir == null) configDir = System.getProperty("user.home");
            configDir += File.separator + appName;
        } else if (os.contains("mac")) {
            configDir = System.getProperty("user.home") + "/Library/Application Support/" + appName;
        } else {
            // Linux / Unix
            String xdg = System.getenv("XDG_CONFIG_HOME");
            if (xdg == null || xdg.isEmpty()) {
                configDir = System.getProperty("user.home") + "/.config/" + appName;
            } else {
                configDir = xdg + "/" + appName;
            }
        }
        return Path.of(configDir, fileName);
    }
}
