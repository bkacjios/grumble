package gg.grumble.client;

import gg.grumble.client.utils.ExceptionHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GrumbleFxApplication extends Application {
    private static final Logger LOG = LogManager.getLogger(GrumbleFxApplication.class);

    private AppContext appContext;

    @Override
    public void init() {
        Thread.currentThread().setName("main");
        ExceptionHandler.installHandlerForCurrentThread();

        appContext = new AppContext(getHostServices());
    }

    @Override
    public void start(Stage stage) {
        Thread.currentThread().setName("javafx");
        ExceptionHandler.installHandlerForCurrentThread();
        appContext.primaryStageHolder().setStage(stage);
        try {
            appContext.fxmlLoader().createWindow(stage, "/fxml/main.fxml");
            stage.setTitle("Grumble");
            stage.show();
            stage.centerOnScreen();
        } catch (Exception e) {
            LOG.error("Error starting JavaFX application", e);
            ExceptionHandler.show(e);
        }
    }

    @Override
    public void stop() {
        if (appContext != null) {
            appContext.close();
        }
        Platform.exit();
        System.exit(0);
    }

}
