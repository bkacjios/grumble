package gg.grumble.core.client;

import gg.grumble.mumble.MumbleProto;

public class MumbleUser {
    // These will never change
    private final MumbleClient client;
    private final int session;

    // These can be updated
    private String name;
    private int userId;
    private boolean mute;
    private boolean deaf;
    private boolean suppressed;
    private boolean selfMute;
    private boolean selfDeaf;

    public MumbleUser(MumbleClient client, int session) {
        this.client = client;
        this.session = session;
    }

    public void update(MumbleProto.UserState state) {
        if (state.hasName()) {
            this.name = state.getName();
        }
        if (state.hasUserId()) {
            this.userId = state.getUserId();
        }
        if (state.hasMute()) {
            this.mute = state.getMute();
        }
        if (state.hasDeaf()) {
            this.deaf = state.getDeaf();
        }
    }

    public MumbleClient getClient() {
        return client;
    }

    public int getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public int getUserId() {
        return userId;
    }

    public boolean isMute() {
        return mute;
    }

    public boolean isDeaf() {
        return deaf;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public boolean isSelfMute() {
        return selfMute;
    }

    public boolean isSelfDeaf() {
        return selfDeaf;
    }
}
