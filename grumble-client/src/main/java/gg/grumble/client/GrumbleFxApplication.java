package gg.grumble.client;

import gg.grumble.client.services.FxmlLoaderService;
import gg.grumble.client.utils.ExceptionHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class GrumbleFxApplication extends Application {
    private static final Logger LOG = LogManager.getLogger(GrumbleFxApplication.class);

    private ConfigurableApplicationContext context;
    private FxmlLoaderService fxmlLoaderService;

    @Override
    public void init() {
        Thread.currentThread().setName("main");
        ExceptionHandler.installHandlerForCurrentThread();

        SpringApplicationBuilder builder = new SpringApplicationBuilder(GrumbleLauncherConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(ctx ->
                        ctx.getBeanFactory()
                                .registerSingleton("hostServices", getHostServices())
                );

        String[] args = getParameters().getRaw().toArray(new String[0]);
        context = builder.run(args);
        fxmlLoaderService = context.getBean(FxmlLoaderService.class);
    }

    @Override
    public void start(Stage stage) {
        Thread.currentThread().setName("javafx");
        ExceptionHandler.installHandlerForCurrentThread();
        try {
            fxmlLoaderService.createWindow(stage, "/fxml/main.fxml");
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
        context.stop();
        Platform.exit();
        System.exit(0);
    }

}
