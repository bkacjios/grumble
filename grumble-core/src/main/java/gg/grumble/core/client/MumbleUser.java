package gg.grumble.core.client;

import gg.grumble.mumble.MumbleProto;

import java.util.Collections;
import java.util.List;

public class MumbleUser {
    private final MumbleClient client;
    private final int session;

    private Integer actor;
    private String name;
    private int userId;
    private Integer channelId;

    private boolean mute;
    private boolean deaf;
    private boolean suppressed;
    private boolean selfMute;
    private boolean selfDeaf;
    private boolean prioritySpeaker;
    private boolean recording;

    private String comment;
    private String hash;

    private byte[] texture;
    private byte[] commentHash;
    private byte[] textureHash;

    private List<String> temporaryAccessTokens = Collections.emptyList();
    private List<Integer> listeningChannelAdd = Collections.emptyList();
    private List<Integer> listeningChannelRemove = Collections.emptyList();

    public MumbleUser(MumbleClient client, int session) {
        this.client = client;
        this.session = session;
    }

    public void update(MumbleProto.UserState state) {
        if (state.hasSession() && state.getSession() != this.session) {
            throw new IllegalArgumentException("Session mismatch: expected " + this.session + ", got " + state.getSession());
        }

        if (state.hasActor()) this.actor = state.getActor();
        if (state.hasName()) this.name = state.getName();
        if (state.hasUserId()) this.userId = state.getUserId();
        if (state.hasChannelId()) this.channelId = state.getChannelId();

        if (state.hasMute()) this.mute = state.getMute();
        if (state.hasDeaf()) this.deaf = state.getDeaf();
        if (state.hasSuppress()) this.suppressed = state.getSuppress();
        if (state.hasSelfMute()) this.selfMute = state.getSelfMute();
        if (state.hasSelfDeaf()) this.selfDeaf = state.getSelfDeaf();
        if (state.hasPrioritySpeaker()) this.prioritySpeaker = state.getPrioritySpeaker();
        if (state.hasRecording()) this.recording = state.getRecording();

        if (state.hasComment()) this.comment = state.getComment();
        if (state.hasHash()) this.hash = state.getHash();

        if (state.hasTexture()) this.texture = state.getTexture().toByteArray();
        if (state.hasCommentHash()) this.commentHash = state.getCommentHash().toByteArray();
        if (state.hasTextureHash()) this.textureHash = state.getTextureHash().toByteArray();

        if (!state.getTemporaryAccessTokensList().isEmpty()) {
            this.temporaryAccessTokens = state.getTemporaryAccessTokensList();
        }

        if (!state.getListeningChannelAddList().isEmpty()) {
            this.listeningChannelAdd = state.getListeningChannelAddList();
        }

        if (!state.getListeningChannelRemoveList().isEmpty()) {
            this.listeningChannelRemove = state.getListeningChannelRemoveList();
        }
    }

    public MumbleClient getClient() { return client; }
    public int getSession() { return session; }
    public Integer getActor() { return actor; }
    public String getName() { return name; }
    public int getUserId() { return userId; }
    public Integer getChannelId() { return channelId; }

    public boolean isMute() { return mute; }
    public boolean isDeaf() { return deaf; }
    public boolean isSuppressed() { return suppressed; }
    public boolean isSelfMute() { return selfMute; }
    public boolean isSelfDeaf() { return selfDeaf; }
    public boolean isPrioritySpeaker() { return prioritySpeaker; }
    public boolean isRecording() { return recording; }

    public String getComment() { return comment; }
    public String getHash() { return hash; }

    public byte[] getTexture() { return texture; }
    public byte[] getCommentHash() { return commentHash; }
    public byte[] getTextureHash() { return textureHash; }

    public List<String> getTemporaryAccessTokens() { return temporaryAccessTokens; }
    public List<Integer> getListeningChannelAdd() { return listeningChannelAdd; }
    public List<Integer> getListeningChannelRemove() { return listeningChannelRemove; }

    @Override
    public String toString() {
        return "MumbleUser{" +
                "session=" + session +
                ", name='" + name + '\'' +
                ", userId=" + userId +
                ", channelId=" + channelId +
                ", mute=" + mute +
                ", deaf=" + deaf +
                ", suppressed=" + suppressed +
                ", selfMute=" + selfMute +
                ", selfDeaf=" + selfDeaf +
                ", prioritySpeaker=" + prioritySpeaker +
                ", recording=" + recording +
                '}';
    }
}