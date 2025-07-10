package gg.grumble.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
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
    private void init() throws IOException {
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
