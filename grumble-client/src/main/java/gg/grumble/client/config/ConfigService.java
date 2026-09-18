package gg.grumble.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static gg.grumble.client.utils.FileUtils.getAppConfigPath;

public class ConfigService {
    private final Path configPath;
    private final ObjectMapper mapper = new ObjectMapper();
    private ApplicationConfig config;

    public ConfigService() throws IOException {
        this.configPath = getAppConfigPath(AppProperties.appName(), "config.json");
        try {
            config = mapper.readValue(configPath.toFile(), ApplicationConfig.class);
        } catch (IOException e) {
            config = new ApplicationConfig();
            saveConfig();
        }
    }

    public ApplicationConfig getConfig() {
        return config;
    }

    public void saveConfig() throws IOException {
        Files.createDirectories(configPath.getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(configPath.toFile(), config);
    }
}
