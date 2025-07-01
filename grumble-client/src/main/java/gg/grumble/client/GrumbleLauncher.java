package gg.grumble.client;

import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public class GrumbleLauncher {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GrumbleLauncherConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        GrumbleFxApplication.setApplicationContext(context);
        Application.launch(GrumbleFxApplication.class, args);
    }
}
