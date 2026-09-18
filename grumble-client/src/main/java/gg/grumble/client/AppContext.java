package gg.grumble.client;

import gg.grumble.client.components.LocaleProvider;
import gg.grumble.client.components.PrimaryStageHolder;
import gg.grumble.client.config.ConfigService;
import gg.grumble.client.controllers.AddServerController;
import gg.grumble.client.controllers.ConnectController;
import gg.grumble.client.controllers.GrumbleController;
import gg.grumble.client.controllers.UserStatsController;
import gg.grumble.client.notifications.NotificationService;
import gg.grumble.client.services.FxmlLoaderService;
import gg.grumble.client.services.LanguageService;
import gg.grumble.client.services.LinkUrlService;
import gg.grumble.client.services.MumbleServerListService;
import gg.grumble.core.client.MumbleClient;
import javafx.application.HostServices;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The application's hand-wired object graph. Shared services are created once here, and FXML controllers are built
 * on demand through {@link #createController(Class)}.
 */
public final class AppContext {
    private final HostServices hostServices;

    private final MumbleClient client;
    private final PrimaryStageHolder primaryStageHolder;
    private final LanguageService lang;
    private final LinkUrlService linkService;
    private final MumbleServerListService serverListService;
    private final ConfigService configService;
    private final FxmlLoaderService fxmlLoaderService;

    public AppContext(HostServices hostServices) {
        this.hostServices = hostServices;

        this.client = new MumbleClient();
        this.primaryStageHolder = new PrimaryStageHolder();
        this.lang = new LanguageService(new LocaleProvider());
        this.linkService = new LinkUrlService();
        this.serverListService = new MumbleServerListService();
        try {
            this.configService = new ConfigService();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load the application config", e);
        }
        this.fxmlLoaderService = new FxmlLoaderService(this::createController);

        // Nothing refers to it, it just listens to the client and raises desktop notifications.
        new NotificationService(lang, client, primaryStageHolder).initialize();
    }

    public FxmlLoaderService fxmlLoader() {
        return fxmlLoaderService;
    }

    public PrimaryStageHolder primaryStageHolder() {
        return primaryStageHolder;
    }

    public void close() {
        client.close();
    }

    /**
     * Builds a fresh controller for every window that is loaded. Controllers hold per-window state (for example
     * {@link ConnectController} shuts its ping queue down when it closes), so they can't be shared between openings.
     */
    private Object createController(Class<?> type) {
        if (type == GrumbleController.class) {
            try {
                return new GrumbleController(client, lang, fxmlLoaderService, linkService, hostServices);
            } catch (LineUnavailableException e) {
                throw new IllegalStateException("Audio device unavailable", e);
            }
        }
        if (type == ConnectController.class) {
            return new ConnectController(serverListService, configService, fxmlLoaderService, client);
        }
        if (type == AddServerController.class) {
            return new AddServerController();
        }
        if (type == UserStatsController.class) {
            return new UserStatsController();
        }
        throw new IllegalArgumentException("No controller registered for " + type.getName());
    }
}
