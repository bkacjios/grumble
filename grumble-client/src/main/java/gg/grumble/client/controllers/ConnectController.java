package gg.grumble.client.controllers;

import gg.grumble.client.config.ConfigService;
import gg.grumble.client.config.ServerConfig;
import gg.grumble.client.models.MumbleServer;
import gg.grumble.client.services.MumbleServerListService;
import gg.grumble.client.utils.WindowIcon;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Component
@WindowIcon("/icons/connect.png")
public class ConnectController implements Initializable {

    private static final int ICON_SIZE = 20;

    @FXML
    private TreeTableView<ServerEntry> treeTableView;
    @FXML
    private TreeTableColumn<ServerEntry, String> nameColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> pingColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> usersColumn;

    private final TreeItem<ServerEntry> favorites = new TreeItem<>(new ServerEntry("Favorites"));
    private final TreeItem<ServerEntry> lan = new TreeItem<>(new ServerEntry("LAN"));
    private final TreeItem<ServerEntry> internet = new TreeItem<>(new ServerEntry("Public Internet"));

    private final MumbleServerListService serverListService;
    private final ConfigService configService;

    public ConnectController(MumbleServerListService serverListService, ConfigService configService) {
        this.serverListService = serverListService;
        this.configService = configService;
    }

    private <T> Callback<TreeTableColumn<ServerEntry, T>, TreeTableCell<ServerEntry, T>> createBlankCellFactory() {
        return col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                } else {
                    TreeItem<ServerEntry> ti = getTreeTableView().getTreeItem(getIndex());
                    boolean isCategory = ti != null && ti.getParent() == treeTableView.getRoot();
                    setText(isCategory
                            ? ""
                            : (item == null ? "" : item.toString()));
                }
            }
        };
    }

    private ImageView fitIcon(Image img) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(ICON_SIZE);
        iv.setFitHeight(ICON_SIZE);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setCache(true);
        iv.setCacheHint(CacheHint.SPEED);
        return iv;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        treeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // bind columns to ServerEntry properties
        nameColumn.setCellValueFactory(c -> c.getValue().getValue().nameProperty());
        pingColumn.setCellValueFactory(c -> c.getValue().getValue().pingProperty().asObject());
        usersColumn.setCellValueFactory(c -> c.getValue().getValue().usersProperty().asObject());
        nameColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // always fetch the *current* treeItem for this row
                    TreeItem<ServerEntry> ti = getTreeTableView().getTreeItem(getIndex());
                    setText(item);
                    setAlignment(Pos.CENTER_LEFT);

                    // if it's one of our top-level category nodes, re-apply its icon
                    if (ti != null && ti.getParent() == treeTableView.getRoot()) {
                        setGraphic(ti.getGraphic());
                        setPadding(new Insets(0, 0, 0, -20));
                    } else {
                        // normal leaf rows get no graphic
                        setGraphic(null);
                        setPadding(new Insets(0, 0, 0, 0));
                    }
                }
            }
        });
        pingColumn.setCellFactory(createBlankCellFactory());
        usersColumn.setCellFactory(createBlankCellFactory());

        // create a hidden root so we can show three top-level categories
        TreeItem<ServerEntry> hiddenRoot = new TreeItem<>(new ServerEntry("ROOT"));
        hiddenRoot.setExpanded(true);

        Image favImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/emblem-favorite.png")),
                ICON_SIZE, ICON_SIZE, true, true);
        Image lanImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/network-workgroup.png")),
                ICON_SIZE, ICON_SIZE, true, true);
        Image netImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/connect.png")),
                ICON_SIZE, ICON_SIZE, true, true);

        favorites.setGraphic(fitIcon(favImg));
        lan.setGraphic(fitIcon(lanImg));
        internet.setGraphic(fitIcon(netImg));

        // the three main nodes
        hiddenRoot.getChildren().addAll(Arrays.asList(favorites, lan, internet));

        treeTableView.setRoot(hiddenRoot);
        treeTableView.setShowRoot(false);

        treeTableView.setSortPolicy(tv -> {
            var sortOrder = tv.getSortOrder();
            if (sortOrder.isEmpty()) return true;  // nothing to do

            // we only look at the primary sorted column
            TreeTableColumn<ServerEntry, ?> col = sortOrder.getFirst();
            Comparator<TreeItem<ServerEntry>> itemComparator = getTreeItemComparator(col);

            // apply to each category under the hidden root
            TreeItem<ServerEntry> root = tv.getRoot();
            for (TreeItem<ServerEntry> category : root.getChildren()) {
                FXCollections.sort(category.getChildren(), itemComparator);
            }

            return true;
        });

        loadFavoritesList();
        loadServerList();
    }

    private static Comparator<TreeItem<ServerEntry>> getTreeItemComparator(TreeTableColumn<ServerEntry, ?> col) {
        boolean asc = col.getSortType() == TreeTableColumn.SortType.ASCENDING;

        // build a comparator that extracts the cell value for that column
        Comparator<TreeItem<ServerEntry>> itemComparator = (a, b) -> {
            Object va = col.getCellObservableValue(a).getValue();
            Object vb = col.getCellObservableValue(b).getValue();
            if (va == null && vb == null) return 0;
            if (va == null) return -1;
            if (vb == null) return +1;
            @SuppressWarnings("unchecked")
            Comparable<Object> ca = (Comparable<Object>) va;
            return ca.compareTo(vb);
        };
        if (!asc) itemComparator = itemComparator.reversed();
        return itemComparator;
    }

    private void loadFavoritesList() {
        favorites.getChildren().setAll(configService.getConfig().getFavoriteServerList()
                .stream()
                .map(ServerEntry::new)
                .map(TreeItem::new)
                .toList());
    }

    private void loadServerList() {
        serverListService.fetchServers()
                .subscribe(list -> {
                    List<TreeItem<ServerEntry>> items = list.getServers().stream()
                            .map(ServerEntry::new)
                            .map(TreeItem::new)
                            .collect(Collectors.toList());

                    Platform.runLater(() -> internet.getChildren().setAll(items));
                });
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
        private final StringProperty name = new SimpleStringProperty();
        private final IntegerProperty ca = new SimpleIntegerProperty();
        private final StringProperty continentCode = new SimpleStringProperty();
        private final StringProperty country = new SimpleStringProperty();
        private final StringProperty countryCode = new SimpleStringProperty();
        private final StringProperty ip = new SimpleStringProperty();
        private final IntegerProperty port = new SimpleIntegerProperty();
        private final StringProperty region = new SimpleStringProperty();
        private final StringProperty url = new SimpleStringProperty();
        private final IntegerProperty ping = new SimpleIntegerProperty();
        private final IntegerProperty users = new SimpleIntegerProperty();
        private final StringProperty username = new SimpleStringProperty();

        /**
         * Populate from the XML‐fetched MumbleServer
         */
        public ServerEntry(MumbleServer server) {
            this.name.set(server.getName());
            this.ca.set(server.getCa());
            this.continentCode.set(server.getContinentCode());
            this.country.set(server.getCountry());
            this.countryCode.set(server.getCountryCode());
            this.ip.set(server.getIp());
            this.port.set(server.getPort());
            this.region.set(server.getRegion());
            this.url.set(server.getUrl());
        }

        public ServerEntry(ServerConfig serverConfig) {
            this.name.set(serverConfig.getLabel());
            this.ip.set(serverConfig.getAddress());
            this.port.set(serverConfig.getPort());
            this.username.set(serverConfig.getUsername());
        }

        public ServerEntry(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public IntegerProperty caProperty() {
            return ca;
        }

        public StringProperty continentCodeProperty() {
            return continentCode;
        }

        public StringProperty countryProperty() {
            return country;
        }

        public StringProperty countryCodeProperty() {
            return countryCode;
        }

        public StringProperty ipProperty() {
            return ip;
        }

        public IntegerProperty portProperty() {
            return port;
        }

        public StringProperty regionProperty() {
            return region;
        }

        public StringProperty urlProperty() {
            return url;
        }

        public IntegerProperty pingProperty() {
            return ping;
        }

        public IntegerProperty usersProperty() {
            return users;
        }

        public void setPing(int value) {
            this.ping.set(value);
        }

        public void setUsers(int value) {
            this.users.set(value);
        }

        public String getUsername() {
            return username.get();
        }

        public void setUsername(String username) {
            this.username.set(username);
        }

        public StringProperty usernameProperty() {
            return username;
        }

        public boolean hasUsername() {
            return !username.get().isBlank();
        }
    }
}
