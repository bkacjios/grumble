package gg.grumble.client.controllers;

import gg.grumble.client.audio.OpenALOutput;
import gg.grumble.client.models.MumbleUserFx;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
import gg.grumble.core.models.MumbleChannel;
import gg.grumble.core.models.MumbleUser;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.LineUnavailableException;
import java.util.*;
import java.util.stream.Stream;

@Component
public class GrumbleController {
    private static final Logger LOG = LoggerFactory.getLogger(GrumbleController.class);

    private static final int ICON_SIZE = 20;

    @FXML
    public TreeView<Object> mumbleTree;
    @FXML
    public TextArea chatMessage;

    private final MumbleClient client;

    private final Map<MumbleChannel, TreeItem<Object>> channelNodeMap = new HashMap<>();
    private final Map<MumbleUser, MumbleUserFx> userFxMap = new HashMap<>();
    private final Map<MumbleUser, TreeItem<Object>> userNodeMap = new HashMap<>();

    // Icons
    private Image userIcon;
    private Image userSpeakingIcon;
    private Image userSpeakingMutedIcon;
    private Image userSelfMuteIcon;
    private Image userSelfDeafIcon;
    private Image userServerMuteIcon;
    private Image userServerDeafIcon;

    public GrumbleController() {
        client = new MumbleClient("pi-two.lan");
        client.setAudioOutput(new OpenALOutput());
        client.setVolume(0.1f);
    }

    private void loadIcons() {
        userIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/talking_off.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userSpeakingIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/talking_on.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userSpeakingMutedIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/talking_muted.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userSelfMuteIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/muted_self.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userSelfDeafIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/deafened_self.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userServerMuteIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/muted_server.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
        userServerDeafIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/deafened_server.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
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
            TreeItem<Object> rootItem = buildTree(client.getChannel(0));
            rootItem.setExpanded(true);
            Platform.runLater(() -> {
                mumbleTree.setRoot(rootItem);
                mumbleTree.setShowRoot(true);
            });
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
        client.addEventListener(MumbleEvents.UserStartSpeaking.class, event -> {
            Platform.runLater(() -> {
                MumbleUserFx user = userFxMap.get(event.user());
                if (user != null) user.setSpeaking(true);
            });
        });
        client.addEventListener(MumbleEvents.UserStopSpeaking.class, event -> {
            Platform.runLater(() -> {
                MumbleUserFx user = userFxMap.get(event.user());
                if (user != null) user.setSpeaking(false);
            });
        });
        client.addEventListener(MumbleEvents.UserState.class, event -> {
            Platform.runLater(() -> {
                MumbleUserFx user = userFxMap.get(event.user());
                if (user != null) user.update();
            });
        });
        client.addEventListener(MumbleEvents.UserChangedChannel.class, event -> {
            Platform.runLater(() -> {
                MumbleUser user = event.user();
                removeUserFromChannel(user, event.from());
                addUserToChannel(user, event.to());
            });
        });

        initializeChat();

        mumbleTree.setCellFactory(tv -> new TreeCell<>() {
            private final ImageView user = new ImageView(userIcon);
            private final ImageView userSpeaking = new ImageView(userSpeakingIcon);
            private final ImageView userSpeakingMuted = new ImageView(userSpeakingMutedIcon);
            private final ImageView userSelfMute = new ImageView(userSelfMuteIcon);
            private final ImageView userSelfDeaf = new ImageView(userSelfDeafIcon);
            private final ImageView userServerMute = new ImageView(userServerMuteIcon);
            private final ImageView userServerDeaf = new ImageView(userServerDeafIcon);

            {
                Stream.of(user, userSpeaking, userSpeakingMuted,
                        userSelfMute, userSelfDeaf,
                        userServerDeaf,userServerMute).forEach(view -> {
                    view.setFitWidth(ICON_SIZE);
                    view.setFitHeight(ICON_SIZE);
                });

                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    // only care about double-clicks on the main cell
                    if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() < 2)
                        return;

                    // see if the click landed on the disclosure node
                    Node target = (Node) event.getTarget();
                    Node disclosure = getDisclosureNode();
                    while (target != null) {
                        if (target == disclosure) {
                            // clicked on the arrow, ignore
                            return;
                        }
                        target = target.getParent();
                    }

                    // otherwise it really was a double click on the cell text/value
                    event.consume();
                    TreeItem<Object> item = getTreeItem();
                    if (item != null && item.getValue() != null) {
                        Object value = item.getValue();
                        if (value instanceof MumbleChannel channel) {
                            client.getSelf().moveToChannel(channel);
                        } else if (value instanceof MumbleUserFx userFx) {
                            // TODO: open send-message dialog
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
                    return;
                }

                if (item instanceof MumbleChannel channel) {
                    updateMumbleChannel(channel);
                } else if (item instanceof MumbleUserFx userFx) {
                    updateMumbleUserFx(userFx);
                }
            }

            private void updateMumbleChannel(MumbleChannel channel) {
                setText(channel.getName());
                setGraphic(null);
                setContentDisplay(ContentDisplay.LEFT);
            }

            private void updateMumbleUserFx(MumbleUserFx userFx) {
                // 1) Build a Label bound to the user’s name
                Label nameLabel = new Label();
                nameLabel.textProperty().bind(userFx.nameProperty());

                // 2) Speaking icon on the left
                ImageView speakView = new ImageView();
                speakView.setFitWidth(ICON_SIZE);
                speakView.setFitHeight(ICON_SIZE);
                speakView.imageProperty().bind(Bindings.createObjectBinding(() -> {
                    if (userFx.isSpeaking() && userFx.isLocalMute()) {
                        return userSpeakingMutedIcon;
                    } else if (userFx.isSpeaking()) {
                        return userSpeakingIcon;
                    } else {
                        return userIcon;
                    }
                }, userFx.speakingProperty(), userFx.localMuteProperty()));

                // 3) “Spacer” grows to fill the middle
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // 4) Mute / Deaf icons on the right
                ImageView selfMute = new ImageView(userSelfMuteIcon);
                selfMute.setFitWidth(ICON_SIZE);
                selfMute.setFitHeight(ICON_SIZE);
                selfMute.visibleProperty().bind(userFx.selfMuteProperty());
                selfMute.managedProperty().bind(userFx.selfMuteProperty());

                ImageView selfDeaf = new ImageView(userSelfDeafIcon);
                selfDeaf.setFitWidth(ICON_SIZE);
                selfDeaf.setFitHeight(ICON_SIZE);
                selfDeaf.visibleProperty().bind(userFx.selfDeafProperty());
                selfDeaf.managedProperty().bind(userFx.selfDeafProperty());

                ImageView serverMute = new ImageView(userServerMuteIcon);
                serverMute.setFitWidth(ICON_SIZE);
                serverMute.setFitHeight(ICON_SIZE);
                serverMute.visibleProperty().bind(userFx.muteProperty());
                serverMute.managedProperty().bind(userFx.muteProperty());

                ImageView serverDeaf = new ImageView(userServerDeafIcon);
                serverDeaf.setFitWidth(ICON_SIZE);
                serverDeaf.setFitHeight(ICON_SIZE);
                serverDeaf.visibleProperty().bind(userFx.deafProperty());
                serverDeaf.managedProperty().bind(userFx.deafProperty());

                // 5) Assemble
                HBox box = new HBox(4, speakView, nameLabel, spacer, selfMute, selfDeaf, serverMute, serverDeaf);
                box.setAlignment(Pos.CENTER);
                setText(null);
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
    }

    private void initializeChat() {
        chatMessage.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                // always consume, so nothing is ever auto-inserted
                event.consume();

                if (event.isShiftDown()) {
                    // SHIFT+ENTER → newline
                    int pos = chatMessage.getCaretPosition();
                    chatMessage.insertText(pos, System.lineSeparator());
                } else {
                    // ENTER alone → fire your action
                    String toSend = chatMessage.getText();
                    sendMessage(toSend);
                    chatMessage.clear();
                }
            }
        });
    }

    private Object getSelectedTreeItem() {
        TreeItem<Object> selectedItem = mumbleTree.getSelectionModel().getSelectedItem();

        if (selectedItem != null) {
            return selectedItem.getValue();
        }
        return null;
    }

    private void sendMessage(String message) {
        Object selected = getSelectedTreeItem();
        if (selected instanceof MumbleUserFx user) {
            user.getUser().message(message);
        } else if (selected instanceof MumbleChannel channel) {
            channel.message(message);
        } else {
            LOG.error("Attempted to send message to unsupported tree object: {}", selected);
        }
    }

    /**
     * Build the tree view for Channels and Users
     */
    private TreeItem<Object> buildTree(MumbleChannel channel) {
        List<TreeItem<Object>> children = new ArrayList<>();

        // 1) add all users (sorted) first
        channel.getUsers().stream()
                .map(MumbleUserFx::new)
                .sorted(this::sortUser)
                .forEach(fx -> {
                    TreeItem<Object> userItem = new TreeItem<>(fx);
                    userFxMap.put(fx.getUser(), fx);
                    userNodeMap.put(fx.getUser(), userItem);
                    children.add(userItem);
                });

        // 2) then add all sub-channels (sorted)
        channel.getChildren().stream()
                .sorted(this::sortChannel)
                .forEach(sub -> children.add(buildTree(sub)));

        TreeItem<Object> channelItem = new TreeItem<>(channel);
        channelItem.getChildren().setAll(children);
        channelNodeMap.put(channel, channelItem);
        return channelItem;
    }

    /**
     * Expand this node and all of its parent nodes
     */
    private void expandPath(TreeItem<?> item) {
        TreeItem<?> current = item;
        while (current != null) {
            current.setExpanded(true);
            current = current.getParent();
        }
    }

    /**
     * Collapse this node and any parent that has no MumbleUserFx descendants left.
     */
    private void collapsePathIfEmpty(TreeItem<?> item) {
        TreeItem<?> current = item;
        while (current != null) {
            boolean hasUser = current.getChildren().stream()
                    .anyMatch(child -> {
                        // if child is a user node...
                        if (child.getValue() instanceof MumbleUserFx) return true;
                        // or if it’s a channel, but its subtree contains a user
                        return containsUserDescendant(child);
                    });
            if (hasUser) {
                // once we hit an ancestor that still has someone below, stop collapsing
                break;
            }
            // otherwise collapse and continue up
            current.setExpanded(false);
            current = current.getParent();
        }
    }

    /**
     * Recursively check if any descendant of this node is a user.
     */
    private boolean containsUserDescendant(TreeItem<?> node) {
        for (TreeItem<?> child : node.getChildren()) {
            if (child.getValue() instanceof MumbleUserFx) return true;
            if (containsUserDescendant(child)) return true;
        }
        return false;
    }

    // Adds a MumbleUser to MumbleChannel and UI tree in sorted position
    private void addUserToChannel(MumbleUser user, MumbleChannel channel) {
        TreeItem<Object> channelItem = channelNodeMap.get(channel);
        if (channelItem == null) return;

        MumbleUserFx userFx = new MumbleUserFx(user);
        userFxMap.put(user, userFx);

        TreeItem<Object> userItem = new TreeItem<>(userFx);
        userNodeMap.put(user, userItem);

        insertSorted(channelItem.getChildren(), userItem, this::mumbleSort);

        expandPath(channelItem);
    }

    // Removes MumbleUser from MumbleChannel and UI tree
    private void removeUserFromChannel(MumbleUser user, MumbleChannel channel) {
        TreeItem<Object> channelItem = channelNodeMap.get(channel);
        if (channelItem == null) return;

        TreeItem<Object> userItem = userNodeMap.remove(user);
        if (userItem != null) {
            channelItem.getChildren().remove(userItem);
        } else {
            // fallback (in case userNodeMap is out of sync)
            channelItem.getChildren().removeIf(child -> {
                Object val = child.getValue();
                return val instanceof MumbleUserFx fx && fx.getUser() == user;
            });
        }

        userFxMap.remove(user);

        collapsePathIfEmpty(channelItem);
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
        parentItem.setExpanded(true);
    }

    // Removes a MumbleChannel and its subtree from UI and map
    private void removeChannel(MumbleChannel channel) {
        TreeItem<Object> item = channelNodeMap.get(channel);
        if (item == null || item.getParent() == null) return;

        TreeItem<Object> parent = item.getParent();
        parent.getChildren().remove(item);
        channelNodeMap.remove(channel);
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

    /**
     * Custom sort for Tree Items.
     * Users come before channels.
     * Channels are sorted by parent, position, then lastly name.
     * Users are sorted by name only.
     */
    private int mumbleSort(TreeItem<Object> a, TreeItem<Object> b) {
        Object va = a.getValue();
        Object vb = b.getValue();

        if (va instanceof MumbleChannel ca && vb instanceof MumbleChannel cb) {
            return sortChannel(ca, cb);
        }
        if (va instanceof MumbleUserFx ua && vb instanceof MumbleUserFx ub) {
            return sortUser(ua, ub);
        }
        // Users before Channels
        return (va instanceof MumbleChannel) ? 1 : -1;
    }

    private int sortUser(MumbleUserFx ua, MumbleUserFx ub) {
        return ua.getName().compareToIgnoreCase(ub.getName());
    }

    private int sortChannel(MumbleChannel ca, MumbleChannel cb) {
        int pidCmp = Long.compare(ca.getParentId(), cb.getParentId());
        if (pidCmp != 0) return pidCmp;

        int posCmp = Integer.compare(ca.getPosition(), cb.getPosition());
        if (posCmp != 0) return posCmp;

        return ca.getName().compareToIgnoreCase(cb.getName());
    }
}
