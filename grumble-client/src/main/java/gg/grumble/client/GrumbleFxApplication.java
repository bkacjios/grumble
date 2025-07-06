package gg.grumble.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

public class GrumbleFxApplication extends Application {
    private static final Logger LOG = LogManager.getLogger(GrumbleFxApplication.class);

    private static ConfigurableApplicationContext context;

    public static void setApplicationContext(ConfigurableApplicationContext applicationContext) {
        context = applicationContext;
    }

    @Override
    public void init() {
        Thread.currentThread().setName("main");
    }

    @Override
    public void start(Stage stage) {
        try {
            // Load your FXML and controller from Spring context
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));

            // Use Spring to resolve controller beans
            fxmlLoader.setControllerFactory(context::getBean);

            Scene scene = new Scene(fxmlLoader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/dark-theme.css"))
                    .toExternalForm());
            stage.setTitle("Grumble");
            stage.setScene(scene);
            stage.show();
            stage.centerOnScreen();
        } catch (Exception e) {
            LOG.error("Error loading main window", e);
        }
    }

    @Override
    public void stop() {
        context.stop();
        Platform.exit();
        System.exit(0);
    }

}
