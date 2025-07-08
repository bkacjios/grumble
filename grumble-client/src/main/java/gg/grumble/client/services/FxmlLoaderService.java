package gg.grumble.client.services;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class FxmlLoaderService {
    private static final Logger LOG = LogManager.getLogger(FxmlLoaderService.class);

    private final Map<String, Pair<Stage, ?>> cachedWindows = new HashMap<>();

    private final ApplicationContext applicationContext;

    public FxmlLoaderService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Stage loadWindow(String resourceName) {
        try {
            URL resourceUrl = getClass().getResource(resourceName);

            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setControllerFactory(applicationContext::getBean);

            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css"))
                    .toExternalForm());

            Stage stage = new Stage();
            stage.setScene(scene);

            return stage;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + resourceName, e);
        }
    }

    public <T> Pair<Stage, T> showWindow(String resourceName) {
        @SuppressWarnings("unchecked")
        Pair<Stage, T> cached = (Pair<Stage, T>) cachedWindows.get(resourceName);

        if (cached != null) {
            Stage stage = cached.getKey();
            if (!stage.isShowing()) {
                stage.show();
            }
            stage.toFront();
            return cached;
        }

        try {
            URL resourceUrl = getClass().getResource(resourceName);

            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setControllerFactory(applicationContext::getBean);

            Parent root = loader.load();
            T controller = loader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css"))
                    .toExternalForm());

            Stage stage = new Stage();
            stage.setScene(scene);

            Pair<Stage, T> stageControllerPair = new Pair<>(stage, controller);
            cachedWindows.put(resourceName, stageControllerPair);

            stage.show();
            stage.close();

            return stageControllerPair;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + resourceName, e);
        }
    }

}