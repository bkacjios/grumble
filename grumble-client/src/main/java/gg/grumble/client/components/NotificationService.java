package gg.grumble.client.components;

import gg.grumble.client.services.LanguageService;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
import gg.grumble.core.models.MumbleChannel;
import gg.grumble.core.models.MumbleUser;
import jakarta.annotation.PostConstruct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@SuppressWarnings("unused")
public class NotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final LanguageService lang;
    private final MumbleClient client;
    private final PrimaryStageHolder primaryStageHolder;

    public NotificationService(LanguageService lang, MumbleClient client, PrimaryStageHolder primaryStageHolder) {
        this.lang = lang;
        this.client = client;
        this.primaryStageHolder = primaryStageHolder;
    }

    @DBusInterfaceName("org.freedesktop.Notifications")
    public interface DbusNotification extends DBusInterface {
        UInt32 Notify(
                String app_name,
                UInt32 replaces_id,
                String app_icon,
                String summary,
                String body,
                String[] actions,
                Map<String, Variant<?>> hints,
                int expire_timeout
        );
    }

    @PostConstruct
    private void initialize() {
        client.addEventListener(MumbleEvents.UserConnected.class, event -> {
            if (event.user().getChannel() == client.getSelf().getChannel()) {
                show("mumble.notification.user.connected",
                        "mumble.event.user.connected.channel", event.user().getName());
            } else {
                show("mumble.notification.user.connected",
                        "mumble.event.user.connected", event.user().getName());
            }
        });
        client.addEventListener(MumbleEvents.UserDisconnected.class, event -> {
            if (event.actor() != null) {
                String titleKey = event.ban() ? "mumble.notification.user.banned" : "mumble.notification.user.kicked";
                String messageKey = event.ban() ? "mumble.event.user.banned" : "mumble.event.user.kicked";
                String reason = Objects.requireNonNullElse(event.reason(),
                        lang.t("mumble.event.user.removed.no_reason"));
                show(titleKey, messageKey, event.user().getUrl(), event.actor().getUrl(), reason);
            } else if (event.user().getChannel() == client.getSelf().getChannel()) {
                show("mumble.notification.user.disconnected",
                        "mumble.event.user.disconnected.channel", event.user().getName());
            } else {
                show("mumble.notification.user.disconnected",
                        "mumble.event.user.disconnected", event.user().getName());
            }
        });
        client.addEventListener(MumbleEvents.UserChangedChannel.class, event -> {
            MumbleUser user = event.user();
            MumbleUser actor = event.actor();
            MumbleChannel from = event.from();
            MumbleChannel to = event.to();
            MumbleChannel selfChannel = client.getSelf().getChannel();

            // You were moved
            if (user == client.getSelf()) {
                if (actor != user) {
                    // You were moved by someone else
                    show("mumble.notification.user.moved", "mumble.event.channel.forced", to.getName(), actor.getName());
                } else {
                    // You moved yourself
                    show("mumble.notification.user.moved", "mumble.event.channel.joined", to.getName());
                }
                return;
            }

            // Another user entered or left your current channel
            boolean enteredSelfChannel = to == selfChannel;
            boolean leftSelfChannel = from == selfChannel;

            if (enteredSelfChannel) {
                if (actor == user) {
                    // User joined your channel on their own
                    show("mumble.notification.user.moved", "mumble.event.channel.entered", user.getName());
                } else {
                    // User was moved into your channel by someone else
                    show("mumble.notification.user.moved", "mumble.event.channel.moved.actor", user.getName(), to.getName(), actor.getName());
                }
            } else if (leftSelfChannel) {
                if (actor == user) {
                    // User left your channel on their own
                    show("mumble.notification.user.moved", "mumble.event.channel.moved", user.getName(), to.getName());
                } else {
                    // User was moved out of your channel by someone else
                    show("mumble.notification.user.moved", "mumble.event.channel.moved.actor", user.getName(), to.getName(), actor.getName());
                }
            }
        });
    }

    private void show(String titleKey, String messageKey, Object... args) {
        if (!primaryStageHolder.isIconified()) return;

        String title = lang.t(titleKey);
        String message = lang.t(messageKey, args);

        try (DBusConnection conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION)) {
            DbusNotification notifications = conn.getRemoteObject(
                    "org.freedesktop.Notifications",
                    "/org/freedesktop/Notifications",
                    DbusNotification.class
            );

            Map<String, Variant<?>> hints = new HashMap<>();
            hints.put("urgency", new Variant<>(1)); // Normal urgency

            notifications.Notify(
                    "Grumble",  // app name
                    new UInt32(0), // replaces_id
                    "mumble",                  // icon
                    title,               // summary
                    message,             // body
                    new String[0],       // actions
                    hints,               // hints
                    5000                 // expire timeout ms
            );
        } catch (Exception e) {
            LOG.error("Error sending notification", e);
        }
    }
}
