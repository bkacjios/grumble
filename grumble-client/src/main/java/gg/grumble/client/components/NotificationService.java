package gg.grumble.client.components;

import gg.grumble.client.services.LanguageService;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEvents;
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

@Component
public class NotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleUser.class);

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
            if (event.user().getChannel() == client.getSelf().getChannel()) {
                show("mumble.notification.user.disconnected",
                        "mumble.event.user.disconnected.channel", event.user().getName());
            } else {
                show("mumble.notification.user.disconnected",
                        "mumble.event.user.disconnected", event.user().getName());
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
                    "",                  // icon
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
