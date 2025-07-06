package gg.grumble.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static gg.grumble.client.utils.FileUtils.getAppConfigPath;

@Component
public class ConfigService {

    private final Path configPath;
    private final ObjectMapper mapper = new ObjectMapper();

    private ApplicationConfig config;

    public ConfigService(@Value("${app.name}") String appName) {
        this.configPath = getAppConfigPath(appName, "config.json");
    }

    @PostConstruct
    public void init() throws IOException {
        loadConfig();
    }

    public ApplicationConfig getConfig() {
        return config;
    }

    public void saveConfig() throws IOException {
        File dir = configPath.getParent().toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create config directory: " + dir);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
    }

    private void loadConfig() throws IOException {
        try {
            config = mapper.readValue(configPath.toFile(), ApplicationConfig.class);
        } catch (IOException e) {
            config = new ApplicationConfig();
            saveConfig();
        }
    }
}
