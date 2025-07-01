package gg.grumble.core.enums;

import java.util.HashMap;
import java.util.Map;

public enum MumbleMessageType {
    VERSION(0),
    UDP_TUNNEL(1),
    AUTHENTICATE(2),
    PING(3),
    SERVER_REJECT(4),
    SERVER_SYNC(5),
    CHANNEL_REMOVE(6),
    CHANNEL_STATE(7),
    USER_REMOVE(8),
    USER_STATE(9),
    BAN_LIST(10),
    TEXT_MESSAGE(11),
    PERMISSION_DENIED(12),
    ACL(13),
    QUERY_USERS(14),
    CRYPT_SETUP(15),
    CONTEXT_ACTION_MOD(16),
    CONTEXT_ACTION(17),
    USER_LIST(18),
    VOICE_TARGET(19),
    PERMISSION_QUERY(20),
    CODEC_VERSION(21),
    USER_STATS(22),
    REQUEST_BLOB(23),
    SERVER_CONFIG(24),
    SUGGEST_CONFIG(25),
    PLUGIN_DATA_TRANSMISSION(26);

    private final int id;

    private static final Map<Integer, MumbleMessageType> ID_MAP = new HashMap<>();

    MumbleMessageType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    static {
        for (MumbleMessageType type : values()) {
            ID_MAP.put(type.getId(), type);
        }
    }

    public static MumbleMessageType fromId(int id) {
        return ID_MAP.get(id);
    }
}
