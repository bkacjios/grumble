package gg.grumble.core.enums;

import java.util.HashMap;
import java.util.Map;

public enum MumblePacketTypeProtobuf {
    AUDIO(0),
    PING(1);

    private final int id;

    MumblePacketTypeProtobuf(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    private static final Map<Integer, MumblePacketTypeProtobuf> BY_ID = new HashMap<>();
    static {
        for (MumblePacketTypeProtobuf type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    public static MumblePacketTypeProtobuf fromId(byte id) {
        return BY_ID.get(Byte.toUnsignedInt(id));
    }
}
