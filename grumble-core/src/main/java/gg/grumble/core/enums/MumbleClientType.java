package gg.grumble.core.enums;

public enum MumbleClientType {
    NORMAL(0),
    BOT(1);

    private final int typeId;

    MumbleClientType(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }
}
