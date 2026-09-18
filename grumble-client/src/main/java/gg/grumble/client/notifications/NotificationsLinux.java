package gg.grumble.client.notifications;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class NotificationsLinux implements Notifications {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationsLinux.class);

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

    @Override
    public void show(String title, String message, int timeoutMs) {
        try (DBusConnection conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION)) {
            DbusNotification notifications = conn.getRemoteObject(
                    "org.freedesktop.Notifications",
                    "/org/freedesktop/Notifications",
                    DbusNotification.class
            );

            Map<String, Variant<?>> hints = new HashMap<>();
            hints.put("urgency", new Variant<>(1)); // normal

            notifications.Notify(
                    "Grumble",
                    new UInt32(0),
                    "mumble",
                    title,
                    message,
                    new String[0],
                    hints,
                    timeoutMs
            );
        } catch (Exception e) {
            LOG.error("Error sending Linux D-Bus notification", e);
        }
    }
}
