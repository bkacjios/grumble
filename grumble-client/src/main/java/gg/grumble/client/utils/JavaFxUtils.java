package gg.grumble.client.utils;

import javafx.application.Platform;

public class JavaFxUtils {

    private JavaFxUtils() {}

    public static void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

}
