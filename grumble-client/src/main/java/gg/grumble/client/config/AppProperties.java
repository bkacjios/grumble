package gg.grumble.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;

/** Read-only view of the bundled {@code application.properties}. */
public final class AppProperties {
    private static final String RESOURCE = "/application.properties";
    private static final Properties PROPERTIES = load();

    private AppProperties() {
    }

    public static String appName() {
        return require("app.name");
    }

    public static String serverListUrl() {
        return require("mumble.server.url");
    }

    public static Duration serverListFetchTimeout() {
        return Duration.ofSeconds(Long.parseLong(require("mumble.server.fetch.timeout")));
    }

    private static String require(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing property '" + key + "' in " + RESOURCE);
        }
        return value.trim();
    }

    private static Properties load() {
        try (InputStream in = AppProperties.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
    }
}
