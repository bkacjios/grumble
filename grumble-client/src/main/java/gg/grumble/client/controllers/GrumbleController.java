package gg.grumble.client.controllers;

import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
import gg.grumble.core.models.MumbleChannel;
import gg.grumble.core.models.MumbleUser;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GrumbleController {
    private static final Logger LOG = LoggerFactory.getLogger(GrumbleController.class);

    @FXML
    public TreeView<Object> mumbleTree;

    private final MumbleClient client;

    private final Map<MumbleChannel, TreeItem<Object>> channelNodeMap = new HashMap<>();

    // Icons
    private Image channelIcon;
    private Image userIcon;
    private Image mutedIcon;

    public GrumbleController() {
        client = new MumbleClient("pi-two.lan");
    }

    public void initialize() {
        loadIcons();

        client.connect();
        client.addEventListener(MumbleEvents.Connected.class, event -> {
            client.authenticate("Java-BOT");
        });
        client.addEventListener(MumbleEvents.Disconnected.class, event -> {
            LOG.warn("Disconnected from mumble server: {}", event.reason());
        });
        client.addEventListener(MumbleEvents.ServerSync.class, event -> {
            LOG.info("Server synced");
            TreeItem<Object> rootItem = buildTree(client.getChannel(0), true);
            if (rootItem != null) {
                rootItem.setExpanded(true);
                Platform.runLater(() -> {
                    mumbleTree.setRoot(rootItem);
                    mumbleTree.setShowRoot(true);
                });
            }
        });
        client.addEventListener(MumbleEvents.UserConnected.class, event -> {
            Platform.runLater(() -> addUserToChannel(event.user(), event.user().getChannel()));
        });
        client.addEventListener(MumbleEvents.UserRemove.class, event -> {
            Platform.runLater(() -> removeUserFromChannel(event.user(), event.user().getChannel()));
        });
        client.addEventListener(MumbleEvents.ChannelCreated.class, event -> {
            Platform.runLater(() -> createChannel(event.channel()));
        });
        client.addEventListener(MumbleEvents.ChannelRemove.class, event -> {
            Platform.runLater(() -> removeChannel(event.channel()));
        });

        mumbleTree.setCellFactory(tv -> new TreeCell<>() {
            private final ImageView userView = new ImageView(userIcon);
            private final ImageView mutedView = new ImageView(mutedIcon);

            {
                userView.setFitWidth(16);
                userView.setFitHeight(16);
                mutedView.setFitWidth(16);
                mutedView.setFitHeight(16);

                // Intercept double-click to prevent expand/collapse and trigger custom logic
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getClickCount() > 2 && event.getButton() == MouseButton.PRIMARY) {
                        event.consume();
                    } else if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                        event.consume(); // prevent collapse/expand
                        TreeItem<Object> item = getTreeItem();
                        if (item != null && item.getValue() != null) {
                            Object value = item.getValue();
                            if (value instanceof MumbleChannel channel) {
                                LOG.info("Double-clicked channel: {}", channel.getName());
                                client.getSelf().moveToChannel(channel);
                            } else if (value instanceof MumbleUser user) {
                                LOG.info("Double-clicked user: {}", user.getName());
                            }
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof MumbleChannel channel) {
                    setText(channel.getName());
                    setGraphic(null); // optional: you could set a channel icon here
                } else if (item instanceof MumbleUser user) {
                    setText(user.getName());
                    setGraphic(user.isMute() ? mutedView : userView);
                }
            }
        });
    }

    private void loadIcons() {
        channelIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/channel.png")).toExternalForm(),
                16, 16, true, true
        );
        userIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/talking_off.png")).toExternalForm(),
                16, 16, true, true
        );
        mutedIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/muted_self.png")).toExternalForm(),
                16, 16, true, true
        );
    }

    private TreeItem<Object> buildTree(MumbleChannel channel, boolean isRoot) {
        List<TreeItem<Object>> children = new ArrayList<>();

        // Sort subchannels by parentID, position, name
        List<MumbleChannel> sortedChannels = channel.getChildren().stream()
                .sorted(Comparator
                        .comparingLong(MumbleChannel::getParentId)
                        .thenComparingLong(MumbleChannel::getPosition)
                        .thenComparing(c -> c.getName().toLowerCase()))
                .toList();

        for (MumbleChannel sub : sortedChannels) {
            TreeItem<Object> subItem = buildTree(sub, false);
            if (subItem != null) {
                children.add(subItem);
            }
        }

        // Sort users alphabetically
        List<MumbleUser> sortedUsers = channel.getUsers().stream()
                .sorted(Comparator.comparing(MumbleUser::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (MumbleUser MumbleUser : sortedUsers) {
            children.add(new TreeItem<>(MumbleUser));
        }

        if (children.isEmpty() && !isRoot) {
            return null;
        }

        TreeItem<Object> channelItem = new TreeItem<>(channel);
        ObservableList<TreeItem<Object>> childrenObs = channelItem.getChildren();
        childrenObs.addAll(children);
        channelNodeMap.put(channel, channelItem);
        return channelItem;
    }

    // Adds a MumbleUser to MumbleChannel and UI tree in sorted position
    private void addUserToChannel(MumbleUser user, MumbleChannel channel) {
        channel.getUsers().add(user);
        TreeItem<Object> channelItem = channelNodeMap.get(channel);
        if (channelItem == null) return;

        TreeItem<Object> userItem = new TreeItem<>(user);

        ObservableList<TreeItem<Object>> children = channelItem.getChildren();
        insertSorted(children, userItem, this::mumbleSort);
    }

    // Removes MumbleUser from MumbleChannel and UI tree
    private void removeUserFromChannel(MumbleUser user, MumbleChannel channel) {
        channel.getUsers().remove(user);
        TreeItem<Object> channelItem = channelNodeMap.get(channel);
        if (channelItem == null) return;

        channelItem.getChildren().removeIf(child -> {
            Object val = child.getValue();
            return val instanceof MumbleUser u && u == user;
        });

        pruneIfEmpty(channelItem);
    }

    private void createChannel(MumbleChannel channel) {
        TreeItem<Object> parentItem = channelNodeMap.get(channel.getParent());
        if (parentItem == null) {
            LOG.warn("Parent channel not found in tree for new channel: {}", channel.getName());
            return;
        }

        TreeItem<Object> newChannelItem = new TreeItem<>(channel);
        channelNodeMap.put(channel, newChannelItem);

        insertSorted(parentItem.getChildren(), newChannelItem, this::mumbleSort);
        parentItem.setExpanded(true); // optionally expand on new children
    }

    // Removes a MumbleChannel and its subtree from UI and map
    private void removeChannel(MumbleChannel channel) {
        TreeItem<Object> item = channelNodeMap.get(channel);
        if (item == null || item.getParent() == null) return;

        TreeItem<Object> parent = item.getParent();
        parent.getChildren().remove(item);
        channelNodeMap.remove(channel);
        pruneIfEmpty(parent);
    }

    // Removes empty channels recursively (hides empty ones)
    private void pruneIfEmpty(TreeItem<Object> item) {
        if (item == null || !(item.getValue() instanceof MumbleChannel)) return;

        if (item.getChildren().isEmpty() && item.getParent() != null) {
            TreeItem<Object> parent = item.getParent();
            parent.getChildren().remove(item);
            channelNodeMap.values().remove(item);
            pruneIfEmpty(parent);
        }
    }

    // Inserts item into list maintaining Mumble sort order
    private void insertSorted(ObservableList<TreeItem<Object>> list, TreeItem<Object> newItem, Comparator<TreeItem<Object>> cmp) {
        int i = 0;
        for (; i < list.size(); i++) {
            if (cmp.compare(newItem, list.get(i)) < 0) {
                break;
            }
        }
        list.add(i, newItem);
    }

    // Comparator for Channels and Users
    private int mumbleSort(TreeItem<Object> a, TreeItem<Object> b) {
        Object va = a.getValue();
        Object vb = b.getValue();

        if (va instanceof MumbleChannel ca && vb instanceof MumbleChannel cb) {
            int pidCmp = Long.compare(ca.getParentId(), cb.getParentId());
            if (pidCmp != 0) return pidCmp;

            int posCmp = Integer.compare(ca.getPosition(), cb.getPosition());
            if (posCmp != 0) return posCmp;

            return ca.getName().compareToIgnoreCase(cb.getName());
        }
        if (va instanceof MumbleUser ua && vb instanceof MumbleUser ub) {
            return ua.getName().compareToIgnoreCase(ub.getName());
        }
        // Channels before users
        return (va instanceof MumbleChannel) ? -1 : 1;
    }
}
