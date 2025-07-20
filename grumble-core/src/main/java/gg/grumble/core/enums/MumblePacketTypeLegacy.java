package gg.grumble.core.enums;

import java.util.HashMap;
import java.util.Map;

public enum MumblePacketTypeLegacy {
    CELT_ALPHA(0),
    PING(1),
    SPEEX(2),
    CELT_BETA(3),
    OPUS(4);

    private final int id;

    MumblePacketTypeLegacy(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    private static final Map<Integer, MumblePacketTypeLegacy> BY_ID = new HashMap<>();
    static {
        for (MumblePacketTypeLegacy type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    public static MumblePacketTypeLegacy fromId(int id) {
        return BY_ID.get(id);
    }
}
