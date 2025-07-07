package gg.grumble.client.controllers;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

@Component
public class ConnectController implements Initializable {

    @FXML
    private TreeTableView<ServerEntry> treeTableView;
    @FXML
    private TreeTableColumn<ServerEntry, String> serverNameColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> pingColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> usersColumn;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // bind columns to ServerEntry properties
        serverNameColumn.setCellValueFactory(c -> c.getValue().getValue().serverNameProperty());
        pingColumn.setCellValueFactory(c -> c.getValue().getValue().pingProperty().asObject());
        usersColumn.setCellValueFactory(c -> c.getValue().getValue().usersProperty().asObject());

        // create a hidden root so we can show three top-level categories
        TreeItem<ServerEntry> hiddenRoot = new TreeItem<>(new ServerEntry("ROOT", 0, 0));
        hiddenRoot.setExpanded(true);

        // the three main nodes
        TreeItem<ServerEntry> favorites = new TreeItem<>(new ServerEntry("Favorites", 0, 0));
        TreeItem<ServerEntry> lan = new TreeItem<>(new ServerEntry("LAN", 0, 0));
        TreeItem<ServerEntry> internet = new TreeItem<>(new ServerEntry("Public Internet", 0, 0));

        hiddenRoot.getChildren().addAll(Arrays.asList(favorites, lan, internet));

        treeTableView.setRoot(hiddenRoot);
        treeTableView.setShowRoot(false);
    }

    public void onConnect(ActionEvent actionEvent) {
    }

    public void onAddNew(ActionEvent actionEvent) {
    }

    public void onEdit(ActionEvent actionEvent) {
    }

    public void onCancel(ActionEvent actionEvent) {
    }

    /**
     * Simple data-model for each row
     */
    public static class ServerEntry {
        private final StringProperty serverName = new SimpleStringProperty();
        private final IntegerProperty ping = new SimpleIntegerProperty();
        private final IntegerProperty users = new SimpleIntegerProperty();

        public ServerEntry(String name, int ping, int users) {
            this.serverName.set(name);
            this.ping.set(ping);
            this.users.set(users);
        }

        public StringProperty serverNameProperty() {
            return serverName;
        }

        public IntegerProperty pingProperty() {
            return ping;
        }

        public IntegerProperty usersProperty() {
            return users;
        }
    }
}
