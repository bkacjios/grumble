package gg.grumble.client.utils;

import gg.grumble.core.utils.ExceptionUtils;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public class ExceptionHandler {
    private static final Logger LOG = LogManager.getLogger(ExceptionHandler.class);

    public static void installHandlerForCurrentThread() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            LOG.error("Uncaught exception in thread: {}", thread.getName(), throwable);
            // Show the exception on the JavaFX application thread
            Platform.runLater(() -> show(throwable));
        });
    }

    public static void show(Throwable t) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Unexpected Exception");
        alert.setHeaderText("An unexpected error occurred.");
        alert.setContentText(ExceptionUtils.getRootCause(t).getLocalizedMessage());

        Window window = alert.getDialogPane().getScene().getWindow();
        if (window instanceof Stage stage) {
            stage.getIcons().add(new Image(Objects
                    .requireNonNull(ExceptionHandler.class.getResourceAsStream("/icons/error.png"))));
        }

        alert.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(ExceptionHandler.class.getResource("/styles/dark-theme.css")).toExternalForm()
        );

        // Stack trace as string
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String exceptionText = sw.toString();

        // Expandable text area
        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setFont(Font.font("Monospaced", FontWeight.NORMAL, 12));
        textArea.setPrefColumnCount(80);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        // Resize based on stack trace length, max 20 lines
        int lineCount = exceptionText.split("\r\n|\r|\n").length;
        textArea.setPrefRowCount(Math.min(lineCount, 20));

        GridPane expandableContent = new GridPane();
        expandableContent.setMaxWidth(Double.MAX_VALUE);
        expandableContent.add(textArea, 0, 0);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane.setVgrow(textArea, Priority.ALWAYS);

        alert.getDialogPane().setExpandableContent(expandableContent);
        alert.getDialogPane().setExpanded(false);

        alert.showAndWait();
    }
}
