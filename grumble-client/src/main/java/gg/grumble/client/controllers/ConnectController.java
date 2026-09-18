package gg.grumble.client.controllers;

import gg.grumble.client.config.ConfigService;
import gg.grumble.client.config.ServerConfig;
import gg.grumble.client.models.MumbleServer;
import gg.grumble.client.services.FxmlLoaderService;
import gg.grumble.client.services.MumbleServerListService;
import gg.grumble.client.utils.Closeable;
import gg.grumble.client.utils.ExceptionHandler;
import gg.grumble.client.utils.JavaFxUtils;
import gg.grumble.client.utils.MumbleBonjourBrowser;
import gg.grumble.client.utils.MumbleServerPingQueue;
import gg.grumble.client.utils.WindowIcon;
import gg.grumble.core.client.MumbleClient;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Component
@WindowIcon("/icons/connect.png")
public class ConnectController implements Initializable, Closeable {

    private static final int ICON_SIZE = 20;

    @FXML
    private TreeTableView<ServerEntry> treeTableView;
    @FXML
    private TreeTableColumn<ServerEntry, String> nameColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> pingColumn;
    @FXML
    private TreeTableColumn<ServerEntry, Integer> usersColumn;

    private final TreeItem<ServerEntry> favorites;
    private final TreeItem<ServerEntry> lan;
    private final TreeItem<ServerEntry> internet;

    private final Map<String, TreeItem<ServerEntry>> lanServices = new HashMap<>();

    private final MumbleServerListService serverListService;
    private final ConfigService configService;
    private final FxmlLoaderService fxmlLoaderService;
    private final MumbleClient client;

    private MumbleBonjourBrowser bonjourBrowser;
    private final MumbleServerPingQueue pingQueue = new MumbleServerPingQueue();

    public ConnectController(MumbleServerListService serverListService, ConfigService configService,
                              FxmlLoaderService fxmlLoaderService, MumbleClient client) {
        this.serverListService = serverListService;
        this.configService = configService;
        this.fxmlLoaderService = fxmlLoaderService;
        this.client = client;

        Image favImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/emblem-favorite.png")),
                ICON_SIZE, ICON_SIZE, true, true);
        Image lanImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/network-workgroup.png")),
                ICON_SIZE, ICON_SIZE, true, true);
        Image netImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/connect.png")),
                ICON_SIZE, ICON_SIZE, true, true);

        favorites = new TreeItem<>(new ServerEntry("Favorites", fitIcon(favImage)));
        lan = new TreeItem<>(new ServerEntry("LAN", fitIcon(lanImage)));
        internet = new TreeItem<>(new ServerEntry("Public Internet", fitIcon(netImage)));
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        treeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // bind columns to ServerEntry properties
        nameColumn.setCellValueFactory(c -> c.getValue().getValue().nameProperty());
        pingColumn.setCellValueFactory(c -> c.getValue().getValue().pingProperty());
        usersColumn.setCellValueFactory(c -> c.getValue().getValue().usersProperty());
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

                    // apply icon if it has one
                    if (ti != null && ti.getValue() != null) {
                        setGraphic(ti.getValue().getIcon());
                    } else {
                        // normal leaf rows get no graphic
                        setGraphic(null);
                    }
                }
            }
        });
        pingColumn.setCellFactory(createBlankCellFactory());
        usersColumn.setCellFactory(createBlankCellFactory());

        // create a hidden root so we can show three top-level categories
        TreeItem<ServerEntry> hiddenRoot = new TreeItem<>(new ServerEntry("ROOT"));
        hiddenRoot.setExpanded(true);

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

        treeTableView.setRowFactory(tv -> {
            TreeTableRow<ServerEntry> row = new TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    TreeItem<ServerEntry> item = row.getTreeItem();
                    if (item != null && !isCategory(item)) {
                        connect(item.getValue());
                    }
                }
            });
            return row;
        });

        loadFavoritesList();
        loadServerList();
        startLanDiscovery();
    }

    private void startLanDiscovery() {
        try {
            bonjourBrowser = new MumbleBonjourBrowser();
            bonjourBrowser.setListener(new MumbleBonjourBrowser.Listener() {
                @Override
                public void onServiceFound(String name, String host, int port) {
                    JavaFxUtils.runOnFxThread(() -> {
                        TreeItem<ServerEntry> existing = lanServices.get(name);
                        if (existing != null) {
                            existing.getValue().ipProperty().set(host);
                            existing.getValue().portProperty().set(port);
                            return;
                        }

                        TreeItem<ServerEntry> item = new TreeItem<>(new ServerEntry(name, host, port));
                        lanServices.put(name, item);
                        lan.getChildren().add(item);
                        pingEntry(item.getValue(), MumbleServerPingQueue.Priority.LAN);
                    });
                }

                @Override
                public void onServiceLost(String name) {
                    JavaFxUtils.runOnFxThread(() -> {
                        TreeItem<ServerEntry> item = lanServices.remove(name);
                        if (item != null) {
                            lan.getChildren().remove(item);
                        }
                    });
                }
            });
            bonjourBrowser.start();
        } catch (IOException e) {
            ExceptionHandler.showLater(e);
        }
    }

    @Override
    public void close() {
        pingQueue.shutdown();

        if (bonjourBrowser == null) return;
        try {
            bonjourBrowser.stop();
        } catch (IOException e) {
            ExceptionHandler.showLater(e);
        }
        bonjourBrowser = null;
    }

    private void pingEntry(ServerEntry entry, MumbleServerPingQueue.Priority priority) {
        pingQueue.ping(entry.ipProperty().get(), entry.portProperty().get(), priority,
                result -> JavaFxUtils.runOnFxThread(() -> {
                    entry.setPing((int) result.pingMillis());
                    entry.setUsers(result.users());
                }),
                error -> { /* leave ping/users blank if the server didn't respond */ }
        );
    }

    private boolean isCategory(TreeItem<ServerEntry> item) {
        return item.getParent() == treeTableView.getRoot();
    }

    private static Comparator<TreeItem<ServerEntry>> getTreeItemComparator(TreeTableColumn<ServerEntry, ?> col) {
        boolean asc = col.getSortType() == TreeTableColumn.SortType.ASCENDING;

        // compares two non-null cell values, honoring the column's sort direction
        Comparator<Object> valueComparator = (va, vb) -> {
            @SuppressWarnings("unchecked")
            Comparable<Object> ca = (Comparable<Object>) va;
            return ca.compareTo(vb);
        };
        if (!asc) valueComparator = valueComparator.reversed();
        Comparator<Object> finalValueComparator = valueComparator;

        // null values (not yet pinged/fetched) always sort last, regardless of direction
        return (a, b) -> {
            Object va = col.getCellObservableValue(a).getValue();
            Object vb = col.getCellObservableValue(b).getValue();
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            return finalValueComparator.compare(va, vb);
        };
    }

    private void loadFavoritesList() {
        List<TreeItem<ServerEntry>> items = configService.getConfig().getFavoriteServerList()
                .stream()
                .map(ServerEntry::new)
                .map(TreeItem::new)
                .toList();
        favorites.getChildren().setAll(items);
        items.forEach(item -> pingEntry(item.getValue(), MumbleServerPingQueue.Priority.FAVORITE));
    }

    private void loadServerList() {
        serverListService.fetchServers()
                .subscribe(
                        list -> {
                            List<TreeItem<ServerEntry>> items = list.getServers().stream()
                                    .map(ServerEntry::new)
                                    .map(TreeItem::new)
                                    .collect(Collectors.toList());
                            JavaFxUtils.runOnFxThread(() -> {
                                internet.getChildren().setAll(items);
                                items.forEach(item -> pingEntry(item.getValue(), MumbleServerPingQueue.Priority.PUBLIC));
                            });
                        },
                        ExceptionHandler::showLater
                );
    }

    public void onConnect(ActionEvent actionEvent) {
        TreeItem<ServerEntry> selected = treeTableView.getSelectionModel().getSelectedItem();
        if (selected == null || isCategory(selected)) return;

        connect(selected.getValue());
    }

    private void connect(ServerEntry entry) {
        String address = entry.ipProperty().get();
        int port = entry.portProperty().get();
        if (address == null || address.isBlank()) return;

        String username = entry.getUsername();
        if (username == null || username.isBlank()) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Username");
            dialog.setHeaderText(null);
            dialog.setContentText("Enter a username:");
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get().isBlank()) return;
            username = result.get().trim();
        }

        client.connect(address, port);
        client.authenticate(username);

        close();
        ((Stage) treeTableView.getScene().getWindow()).close();
    }

    public void onAddNew(ActionEvent actionEvent) {
        Pair<Stage, AddServerController> stageController = fxmlLoaderService.createWindow("/fxml/addServer.fxml");
        Stage stage = stageController.getKey();
        AddServerController controller = stageController.getValue();
        stage.setTitle("Add Server");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setResizable(false);
        stage.setOnShown(e -> stage.centerOnScreen());
        stage.showAndWait();

        ServerConfig newServer = controller.getResult();
        if (newServer == null) return;

        List<ServerConfig> favoriteServerList = new ArrayList<>(configService.getConfig().getFavoriteServerList());
        favoriteServerList.add(newServer);
        configService.getConfig().setFavoriteServerList(favoriteServerList);

        try {
            configService.saveConfig();
        } catch (IOException e) {
            ExceptionHandler.showLater(e);
            return;
        }

        loadFavoritesList();
    }

    public void onEdit(ActionEvent actionEvent) {
    }

    public void onCancel(ActionEvent actionEvent) {
        close();
        ((Stage) treeTableView.getScene().getWindow()).close();
    }

    /**
     * Simple data-model for each row
     */
    public static class ServerEntry {
        private final ImageView icon;
        private final StringProperty name = new SimpleStringProperty();
        private final IntegerProperty ca = new SimpleIntegerProperty();
        private final StringProperty continentCode = new SimpleStringProperty();
        private final StringProperty country = new SimpleStringProperty();
        private final StringProperty countryCode = new SimpleStringProperty();
        private final StringProperty ip = new SimpleStringProperty();
        private final IntegerProperty port = new SimpleIntegerProperty();
        private final StringProperty region = new SimpleStringProperty();
        private final StringProperty url = new SimpleStringProperty();
        private final ObjectProperty<Integer> ping = new SimpleObjectProperty<>();
        private final ObjectProperty<Integer> users = new SimpleObjectProperty<>();
        private final StringProperty username = new SimpleStringProperty();

        /**
         * Populate from the XML‐fetched MumbleServer
         */
        public ServerEntry(MumbleServer server) {
            this.icon = null;
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
            this.icon = null;
            this.name.set(serverConfig.getLabel());
            this.ip.set(serverConfig.getAddress());
            this.port.set(serverConfig.getPort());
            this.username.set(serverConfig.getUsername());
        }

        public ServerEntry(String name) {
            this.icon = null;
            this.name.set(name);
        }

        /**
         * Populate from a discovered LAN (mDNS/Bonjour) server
         */
        public ServerEntry(String name, String ip, int port) {
            this.icon = null;
            this.name.set(name);
            this.ip.set(ip);
            this.port.set(port);
        }

        public ServerEntry(String name, ImageView icon) {
            this.name.set(name);
            this.icon = icon;
        }

        public ImageView getIcon() {
            return icon;
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

        public ObjectProperty<Integer> pingProperty() {
            return ping;
        }

        public ObjectProperty<Integer> usersProperty() {
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
