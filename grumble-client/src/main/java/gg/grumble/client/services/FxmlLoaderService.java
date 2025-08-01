package gg.grumble.client.services;

import gg.grumble.client.utils.UiEventController;
import gg.grumble.client.utils.Closeable;
import gg.grumble.client.utils.StageAware;
import gg.grumble.client.utils.WindowIcon;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

@Service
public class FxmlLoaderService {
    private static final Logger LOG = LogManager.getLogger(FxmlLoaderService.class);

    private final ApplicationContext applicationContext;

    public FxmlLoaderService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public <T> Pair<Stage, T> createWindow(Stage stage, String resourceName) {
        try {
            URL resourceUrl = getClass().getResource(resourceName);

            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setControllerFactory(applicationContext::getBean);

            Parent root = loader.load();
            T controller = loader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css"))
                    .toExternalForm());

            stage.setScene(scene);

            Class<?> clazz = controller.getClass();
            WindowIcon iconAnnotation = clazz.getAnnotation(WindowIcon.class);
            if (iconAnnotation != null) {
                String iconPath = iconAnnotation.value();
                try {
                    stage.getIcons().add(new Image(
                            Objects.requireNonNull(getClass().getResourceAsStream(iconPath))
                    ));
                } catch (Exception e) {
                    LOG.warn("Failed to load window icon: {}", iconPath, e);
                }
            }

            if (controller instanceof Closeable closeable) {
                EventHandler<WindowEvent> handler = new EventHandler<>() {
                    @Override
                    public void handle(WindowEvent e) {
                        closeable.close();
                        stage.removeEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, this);
                    }
                };
                stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, handler);
            }
            if (controller instanceof StageAware stageAware) {
                stageAware.setStage(stage);
            }
            if (controller instanceof UiEventController uiEventController) {
                uiEventController.initializeEventHooks();
            }

            return new Pair<>(stage, controller);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + resourceName, e);
        }
    }

    public <T> Pair<Stage, T> createWindow(String resourceName) {
        Stage stage = new Stage();
        return createWindow(stage, resourceName);
    }

}