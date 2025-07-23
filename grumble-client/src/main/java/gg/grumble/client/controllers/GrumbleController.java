package gg.grumble.client.controllers;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import gg.grumble.client.models.MumbleUserFx;
import gg.grumble.client.services.FxmlLoaderService;
import gg.grumble.client.utils.Closeable;
import gg.grumble.client.utils.ExceptionHandler;
import gg.grumble.client.utils.WindowIcon;
import gg.grumble.core.audio.input.TargetDataLineInputDevice;
import gg.grumble.core.audio.output.SourceDataLineOutputDevice;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
import gg.grumble.core.models.MumbleChannel;
import gg.grumble.core.models.MumbleUser;
import gg.grumble.mumble.MumbleProto;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Pair;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.LineUnavailableException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

@Component
@WindowIcon("/icons/talking_off.png")
public class GrumbleController implements Initializable, Closeable, NativeKeyListener {
    private static final Logger LOG = LoggerFactory.getLogger(GrumbleController.class);

    private static final int ICON_SIZE = 20;

    private record HyperlinkInfo(int start, int end, String href) {
    }

    @FXML
    public TreeView<Object> mumbleTree;
    @FXML
    public StyleClassedTextArea chatArea;
    @FXML
    public TextArea chatMessage;

    private final FxmlLoaderService fxmlLoaderService;
    private final HostServices hostServices;

    private final MumbleClient client = new MumbleClient();

    private final Map<MumbleChannel, TreeItem<Object>> channelNodeMap = new HashMap<>();
    private final Map<MumbleUser, MumbleUserFx> userFxMap = new HashMap<>();
    private final Map<MumbleUser, TreeItem<Object>> userNodeMap = new HashMap<>();

    private final List<HyperlinkInfo> links = new ArrayList<>();

    // Icons
    private Image channelIcon;
    private Image userIcon;
    private Image userSpeakingIcon;
    private Image userSpeakingMutedIcon;
    private Image userSelfMuteIcon;
    private Image userSelfDeafIcon;
    private Image userServerMuteIcon;
    private Image userServerDeafIcon;
    private Image userAuthenticatedIcon;

    public GrumbleController(FxmlLoaderService fxmlLoaderService, HostServices hostServices) throws LineUnavailableException {
        this.fxmlLoaderService = fxmlLoaderService;
        this.hostServices = hostServices;

        client.setAudioOutput(new SourceDataLineOutputDevice());
        client.setAudioInput(new TargetDataLineInputDevice());
        client.setVolume(0.05f);
    }

    private void loadIcons() {
        channelIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/broadcast-solid.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
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
        userAuthenticatedIcon = new Image(
                Objects.requireNonNull(getClass().getResource("/icons/authenticated.png")).toExternalForm(),
                ICON_SIZE, ICON_SIZE, true, true
        );
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_F13) {
            client.setTransmitting(true);
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_F13) {
            client.setTransmitting(false);
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadIcons();
        initializeChat();

        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            LOG.error("Unable to register native hook", e);
        }

        GlobalScreen.addNativeKeyListener(this);

        String hostname = "pi-two.lan";

        client.connect(hostname);
        addMessage(String.format("Connecting to server <span class='log-hostname'>%s</span>", hostname));

        client.addEventListener(MumbleEvents.Connected.class, ignored -> {
            client.authenticate("Java-BOT");
            Platform.runLater(() -> addMessage("Connected."));
        });
        client.addEventListener(MumbleEvents.ServerReject.class, event -> {
            String reason = event.reject().getReason();
            LOG.warn("Rejected from server: {}", reason);
            Platform.runLater(() -> addMessage(String.format("Rejected from server: <span class='log-hostname'>%s</span>", reason)));
        });
        client.addEventListener(MumbleEvents.Disconnected.class, event -> {
            LOG.warn("Disconnected from mumble server: {}", event.reason());
            channelNodeMap.clear();
            userFxMap.clear();
            userNodeMap.clear();
            Platform.runLater(() -> {
                mumbleTree.setRoot(null);
                addMessage("Disconnected from server.");
            });
        });
        client.addEventListener(MumbleEvents.ServerSync.class, event -> {
            TreeItem<Object> rootItem = buildTree(client.getChannel(0));
            rootItem.setExpanded(true);
            Platform.runLater(() -> {
                addMessage("Welcome message: " + event.sync().getWelcomeText());
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
                if (user == client.getSelf()) {
                    addMessage(String.format("You joined %s.", event.to().getUrl()));
                } else if (client.getSelf().getChannel() == event.to()) {
                    addMessage(String.format("%s entered channel.", event.user().getUrl()));
                } else {
                    addMessage(String.format("%s moved to %s.", event.user().getUrl(), event.to().getUrl()));
                }
            });
        });
        client.addEventListener(MumbleEvents.TextMessage.class, event -> {
            Platform.runLater(() -> {
                MumbleProto.TextMessage message = event.message();
                MumbleUser sender = client.getUser(message.getActor());
                addMessage(String.format("%s: %s", sender.getUrl(), message.getMessage()));
                LOG.info("Message: {}", message.getMessage());
            });
        });

        chatMessage.promptTextProperty().bind(
                Bindings.createStringBinding(() -> {
                            TreeItem<Object> sel = mumbleTree.getSelectionModel().getSelectedItem();
                            if (sel == null || sel.getValue() == null) {
                                return "Type message";
                            }
                            Object v = sel.getValue();
                            String name;
                            String type;
                            if (v instanceof MumbleUserFx user) {
                                name = user.getName();
                                type = "user";
                            } else if (v instanceof MumbleChannel ch) {
                                name = ch.getName();
                                type = "channel";
                            } else {
                                name = v.toString();
                                type = "unknown";
                            }
                            return String.format("Type message to %s '%s' here", type, name);
                        },
                        mumbleTree.getSelectionModel().selectedItemProperty()
                ));

        mumbleTree.setCellFactory(ignored -> new TreeCell<>() {
            private final ImageView channelView = new ImageView(channelIcon);
            private final ImageView user = new ImageView(userIcon);
            private final ImageView userSpeaking = new ImageView(userSpeakingIcon);
            private final ImageView userSpeakingMuted = new ImageView(userSpeakingMutedIcon);
            private final ImageView userSelfMute = new ImageView(userSelfMuteIcon);
            private final ImageView userSelfDeaf = new ImageView(userSelfDeafIcon);
            private final ImageView userServerMute = new ImageView(userServerMuteIcon);
            private final ImageView userServerDeaf = new ImageView(userServerDeafIcon);
            private final ImageView userAuthenticated = new ImageView(userAuthenticatedIcon);

            {
                Stream.of(channelView, user, userSpeaking, userSpeakingMuted, userSelfMute, userSelfDeaf,
                        userServerDeaf, userServerMute, userAuthenticated).forEach(view -> {
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
                    setContextMenu(createMumbleUserContextMenu(userFx));
                    updateMumbleUserFx(userFx);
                }
            }

            private void updateMumbleChannel(MumbleChannel channel) {
                setText(channel.getName());
                setGraphic(channelView);
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

                ImageView authenticated = new ImageView(userAuthenticatedIcon);
                authenticated.setFitWidth(ICON_SIZE);
                authenticated.setFitHeight(ICON_SIZE);
                authenticated.visibleProperty().bind(userFx.authenticatedProperty());
                authenticated.managedProperty().bind(userFx.authenticatedProperty());

                // 5) Assemble
                HBox box = new HBox(4, speakView, nameLabel, spacer, selfMute, selfDeaf, serverMute, serverDeaf, authenticated);
                box.setAlignment(Pos.CENTER);
                setText(null);
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
    }

    private ContextMenu createMumbleUserContextMenu(MumbleUserFx userFx) {
        // build the menu *for this user*:
        CheckMenuItem muteItem = new CheckMenuItem("Mute");
        muteItem.selectedProperty().bindBidirectional(userFx.muteProperty());

        CheckMenuItem deafItem = new CheckMenuItem("Deafen");
        deafItem.selectedProperty().bindBidirectional(userFx.deafProperty());

        MenuItem priorityItem = new MenuItem("Priority Speaker");
        priorityItem.setDisable(true);

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        CheckMenuItem localMuteItem = new CheckMenuItem("Local Mute");
        localMuteItem.selectedProperty().bindBidirectional(userFx.localMuteProperty());

        CheckMenuItem ignoreItem = new CheckMenuItem("Ignore Messages");
        // ignoreItem.selectedProperty().bindBidirectional(userFx.ignoreMessagesProperty());

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        // === volume slider row, label above slider ===
        Label volTitle = new Label("Local Volume Adjustment:");

        Slider volSlider = new Slider(-30, 30, userFx.localVolumeProperty().get());
        volSlider.setPrefWidth(120);
        volSlider.valueProperty().bindBidirectional(userFx.localVolumeProperty());

        Label volValue = new Label();
        volValue.textProperty().bind(userFx.localVolumeProperty().asString("%.0f dB"));

        HBox sliderRow = new HBox(4, volSlider, volValue);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        VBox volBox = new VBox(2, volTitle, sliderRow);
        volBox.setAlignment(Pos.CENTER_LEFT);

        CustomMenuItem volItem = new CustomMenuItem(volBox);
        volItem.setHideOnClick(false);


        SeparatorMenuItem sep3 = new SeparatorMenuItem();

        MenuItem nicknameItem = new MenuItem("Set Nickname…");
        nicknameItem.setOnAction(evt -> {
            // TODO show nickname dialog
        });

        SeparatorMenuItem sep4 = new SeparatorMenuItem();

        MenuItem sendMsgItem = new MenuItem("Send Message…");
        sendMsgItem.setOnAction(evt -> {
            // TODO open chat window
        });

        MenuItem infoItem = new MenuItem("Information");
        infoItem.setOnAction(evt -> {
            Pair<Stage, UserStatsController> stageController = fxmlLoaderService.createWindow("/fxml/userStats.fxml");
            Stage stage = stageController.getKey();
            UserStatsController controller = stageController.getValue();
            controller.setClientSession(this.client, userFx.getUser());
            stage.initStyle(StageStyle.UTILITY);
            stage.show();
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.setMinWidth(stage.getWidth());
            stage.setMinHeight(stage.getHeight());
        });

        MenuItem addFriendItem = new MenuItem("Add Friend");
        addFriendItem.setOnAction(evt -> {
            // TODO mark as friend
        });

        return new ContextMenu(
                muteItem,
                deafItem,
                priorityItem,
                sep1,
                localMuteItem,
                ignoreItem,
                sep2,
                volItem,
                sep3,
                nicknameItem,
                sep4,
                sendMsgItem,
                infoItem,
                addFriendItem
        );
    }

    private void initializeChat() {
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        addMessage("Welcome to Mumble.");

        chatArea.setOnMouseClicked(evt -> {
            var hit = chatArea.hit(evt.getX(), evt.getY());
            int pos = hit.getInsertionIndex();
            for (HyperlinkInfo link : links) {
                if (pos >= link.start && pos < link.end) {
                    handleLink(link.href);
                    break;
                }
            }
        });

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

    private void handleLink(String href) {
        if (href.startsWith("channelid://")) {
            long id = Long.parseLong(href.substring("channelid://".length()));
            selectChannel(id);
        } else if (href.startsWith("clientid://")) {
            long session = Long.parseLong(href.substring("clientid://".length()));
            selectUser(session);
        } else if (href.startsWith("http://") || href.startsWith("https://")) {
            // open in system browser
            hostServices.showDocument(href);
        }
    }

    /**
     * Select the channel node for the given channel id
     */
    private void selectChannel(long channelId) {
        channelNodeMap.keySet().stream()
                .filter(ch -> ch.getChannelId() == channelId)
                .findFirst()
                .map(channelNodeMap::get)
                .ifPresent(item -> {
                    Platform.runLater(() -> {
                        expandPath(item);
                        mumbleTree.getSelectionModel().select(item);
                        mumbleTree.scrollTo(mumbleTree.getRow(item));
                    });
                });
    }

    /**
     * Select the user node for the given user session id
     */
    private void selectUser(long session) {
        userNodeMap.keySet().stream()
                .filter(user -> user.getSession() == session)
                .findFirst()
                .map(userNodeMap::get)
                .ifPresent(item -> {
                    Platform.runLater(() -> {
                        expandPath(item);
                        mumbleTree.getSelectionModel().select(item);
                        mumbleTree.scrollTo(mumbleTree.getRow(item));
                    });
                });
    }

    private void addMessage(String message) {
        if (message == null || message.isEmpty()) return;

        // timestamp
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int start = chatArea.getLength();
        chatArea.appendText("[" + time + "] ");
        chatArea.setStyleClass(start, start + time.length() + 2, "timestamp");

        // message body
        appendHtmlFragment(message);

        int len = chatArea.getLength();
        if (len <= 0 || !"\n".equals(chatArea.getText(len-1, len))) {
            chatArea.appendText("\n");
        }

        int nlStart = chatArea.getLength() - 1;
        chatArea.clearStyle(nlStart, nlStart + 1);

        // scroll to bottom
        chatArea.showParagraphAtBottom(chatArea.getCurrentParagraph());
    }

    /**
     * Parses the given HTML fragment and appends it to chatArea,
     * carrying along the `baseStyles` for every text node.
     */
    private void appendHtmlFragment(String html) {
        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(html);
        for (org.jsoup.nodes.Node node : doc.body().childNodes()) {
            // seed with "message" so every run gets your white fill
            recurseAppend(node, new ArrayList<>(List.of("message")));
        }
    }

    private void recurseAppend(org.jsoup.nodes.Node node, List<String> styles) {
        if (node instanceof TextNode) {
            String txt = ((TextNode) node).text();
            if (!txt.isEmpty()) {
                int start = chatArea.getLength();
                chatArea.appendText(txt);
                chatArea.setStyle(start, start + txt.length(), styles);
            }
        } else if (node instanceof Element el) {
            String tag = el.tagName().toLowerCase();

            if ("br".equals(tag)) {
                chatArea.appendText("\n");
                return;
            }

            // clone styles so we don't mutate the parent list
            List<String> newStyles = new ArrayList<>(styles);

//            if ("img".equals(tag)) {
//                String src = el.attr("src");
//                if (src.startsWith("data:image/")) {
//                    String b64 = src.substring(src.indexOf(',') + 1);
//                    byte[] data = Base64.getDecoder().decode(b64);
//                    Image img = new Image(new ByteArrayInputStream(data));
//
//                    ImageView iv = new ImageView(img);
//                    iv.setFitHeight(16);        // thumbnail height
//                    iv.setPreserveRatio(true);
//
//                    // embed at current caret position
//                    int pos = chatArea.getLength();
//                    chatArea.insertNode(pos, iv);
//                }
//                return;
//            }

            if ("a".equals(tag)) {
                String href = el.attr("href");
                int linkStart = chatArea.getLength();

                if (el.className().isBlank()) {
                    newStyles.add("hyperlink");
                } else {
                    newStyles.addAll(Arrays.asList(el.className().split("\\s+")));
                }

                // recurse into the anchor’s children
                for (org.jsoup.nodes.Node child : el.childNodes()) {
                    recurseAppend(child, newStyles);
                }

                // record where this link ends
                int linkEnd = chatArea.getLength();
                links.add(new HyperlinkInfo(linkStart, linkEnd, href));
                return;  // skip the other-tag logic
            }

            // push any new classes for this tag
            switch (tag) {
                case "b", "strong":
                    newStyles.add("bold");
                    break;
                case "i", "em":
                    newStyles.add("italic");
                    break;
                case "u":
                    newStyles.add("underline");
                    break;
                case "span":
                    if (!el.className().isBlank()) {
                        newStyles.addAll(Arrays.asList(el.className().split("\\s+")));
                    }
                    break;
                case "p":
                    // Add a newline before the paragraph unless it's the very start
                    if (chatArea.getLength() > 0) {
                        chatArea.appendText("\n");
                    }
                    break;
                case "table":
                    newStyles.add("table"); // optional: add table class
                    break;
                case "tr":
                    if (chatArea.getLength() > 0) {
                        chatArea.appendText("\n"); // new row
                    }
                    break;
                case "td", "th":
                    newStyles.add("cell"); // optional: style for table cell
                    break;
            }

            // recurse children with the cloned list
            for (org.jsoup.nodes.Node child : el.childNodes()) {
                recurseAppend(child, newStyles);
            }

            // Add spacing after table cells
            if ("td".equals(tag) || "th".equals(tag)) {
                chatArea.appendText("\t");
            }

        }
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
            // Whisper message to selected user
            addMessage(String.format("To %s: %s", user.getUser().getUrl(), message));
            user.getUser().message(message);
        } else if (selected instanceof MumbleChannel channel) {
            // Send message to everyone in selected channel
            addMessage(String.format("To %s: %s", channel.getUrl(), message));
            channel.message(message);
        } else {
            // Send message to everyone in current channel
            MumbleChannel channel = client.getSelf().getChannel();
            addMessage(String.format("To %s: %s", channel.getUrl(), message));
            channel.message(message);
        }
    }

    /**
     * Build the tree view for Channels and Users
     */
    private TreeItem<Object> buildTree(MumbleChannel channel) {
        TreeItem<Object> parentItem = new TreeItem<>(channel);
        channelNodeMap.put(channel, parentItem);

        // 1) add all users (sorted) first
        channel.getUsers().stream()
                .map(MumbleUserFx::new)
                .sorted(this::sortUser)
                .forEach(fx -> {
                    TreeItem<Object> userItem = new TreeItem<>(fx);
                    userFxMap.put(fx.getUser(), fx);
                    userNodeMap.put(fx.getUser(), userItem);
                    parentItem.getChildren().add(userItem);
                });

        // 2) then add all sub-channels (sorted)
        channel.getChildren().stream()
                .sorted(this::sortChannel)
                .forEach(sub -> parentItem.getChildren().add(buildTree(sub)));

        // 3) Expand self & parents if we have users
        if (containsUserDescendant(parentItem)) {
            expandPath(parentItem);
        }

        return parentItem;
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

    public void onConnect(ActionEvent actionEvent) {
        Pair<Stage, ConnectController> stageController = fxmlLoaderService.createWindow("/fxml/connect.fxml");
        Stage stage = stageController.getKey();
        stage.setTitle("Connect");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.show();
        stage.centerOnScreen();
    }

    public void onDisconnect(ActionEvent actionEvent) {
        client.close();
    }

    @Override
    public void close() {
        client.close();
    }
}
