package gg.grumble.client.controllers;

import gg.grumble.client.config.ServerConfig;
import gg.grumble.client.utils.StageAware;
import gg.grumble.client.utils.WindowIcon;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

@Component
@WindowIcon("/icons/connect.png")
public class AddServerController implements Initializable, StageAware {

    private static final Pattern PORT_INPUT = Pattern.compile("\\d{0,5}");

    @FXML
    private TextField addressField;
    @FXML
    private TextField portField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField labelField;
    @FXML
    private Button okButton;

    private Stage stage;
    private ServerConfig result;

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        portField.setTextFormatter(new TextFormatter<>(change ->
                PORT_INPUT.matcher(change.getControlNewText()).matches() ? change : null));

        okButton.disableProperty().bind(
                addressField.textProperty().isEmpty()
                        .or(portField.textProperty().isEmpty())
                        .or(usernameField.textProperty().isEmpty())
        );
    }

    /**
     * @return the server the user entered, or null if the dialog was cancelled.
     */
    public ServerConfig getResult() {
        return result;
    }

    public void onOk(ActionEvent actionEvent) {
        ServerConfig config = new ServerConfig();
        config.setAddress(addressField.getText().trim());
        config.setPort(Integer.parseInt(portField.getText().trim()));
        config.setUsername(usernameField.getText().trim());

        String label = labelField.getText();
        config.setLabel(label == null || label.isBlank() ? config.getAddress() : label.trim());

        result = config;
        stage.close();
    }

    public void onCancel(ActionEvent actionEvent) {
        stage.close();
    }
}
