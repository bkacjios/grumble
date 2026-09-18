package gg.grumble.core.models;

import gg.grumble.core.audio.UserAudioBuffer;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.enums.MumbleMessageType;

import gg.grumble.mumble.MumbleProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MumbleUser {
    private static final Logger LOG = LoggerFactory.getLogger(MumbleUser.class);

    private final MumbleClient client;
    private final long session;

    private String name;
    private long userId;
    private long channelId;

    private boolean mute;
    private boolean deaf;
    private boolean suppressed;
    private boolean selfMute;
    private boolean selfDeaf;
    private boolean localMute;
    private boolean prioritySpeaker;
    private boolean recording;
    private volatile boolean speaking;

    private String comment;
    private String hash;

    private byte[] texture;
    private byte[] commentHash;
    private byte[] textureHash;

    private final Set<Integer> listeningChannels = new LinkedHashSet<>();

    private final UserAudioBuffer audio;

    public MumbleUser(MumbleClient client, long session) {
        this.client = client;
        this.session = session;
        this.audio = new UserAudioBuffer(this);
    }

    public void update(MumbleProto.UserState state) {
        if (state.hasSession() && state.getSession() != this.session) {
            throw new IllegalArgumentException("Session mismatch: expected " + this.session + ", got " + state.getSession());
        }

        if (state.hasName()) this.name = state.getName();
        if (state.hasUserId()) this.userId = Integer.toUnsignedLong(state.getUserId());
        if (state.hasChannelId()) this.channelId = Integer.toUnsignedLong(state.getChannelId());

        if (state.hasMute()) this.mute = state.getMute();
        if (state.hasDeaf()) this.deaf = state.getDeaf();
        if (state.hasSuppress()) this.suppressed = state.getSuppress();
        if (state.hasSelfMute()) this.selfMute = state.getSelfMute();
        if (state.hasSelfDeaf()) this.selfDeaf = state.getSelfDeaf();
        if (state.hasPrioritySpeaker()) this.prioritySpeaker = state.getPrioritySpeaker();
        if (state.hasRecording()) this.recording = state.getRecording();

        if (this.selfDeaf) this.selfMute = true;
        if (this.deaf) this.mute = true;

        if (state.hasComment()) this.comment = state.getComment();
        if (state.hasHash()) this.hash = state.getHash();

        if (state.hasTexture()) this.texture = state.getTexture().toByteArray();
        if (state.hasCommentHash()) this.commentHash = state.getCommentHash().toByteArray();
        if (state.hasTextureHash()) this.textureHash = state.getTextureHash().toByteArray();

        listeningChannels.addAll(state.getListeningChannelAddList());

        for (int remove : state.getListeningChannelRemoveList()) {
            listeningChannels.remove(remove);
        }
    }

    public MumbleClient getClient() {
        return client;
    }

    public long getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public long getUserId() {
        return userId;
    }

    public boolean isRegistered() {
        return userId > 0;
    }

    public long getChannelId() {
        return channelId;
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

    public boolean isLocalMute() {
        return localMute;
    }

    public boolean isPrioritySpeaker() {
        return prioritySpeaker;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public boolean isTransmitting() {
        return audio.isTransmitting();
    }

    public String getComment() {
        return comment;
    }

    public String getHash() {
        return hash;
    }

    public byte[] getTexture() {
        return texture;
    }

    public byte[] getCommentHash() {
        return commentHash;
    }

    public byte[] getTextureHash() {
        return textureHash;
    }

    public List<MumbleChannel> getListeningChannels() {
        return listeningChannels.stream()
                .map(client::getChannel)
                .filter(Objects::nonNull)
                .toList();
    }

    public MumbleChannel getChannel() {
        return client.getChannel(channelId);
    }

    public void pushAudio(long sequence, byte[] opusPayload, boolean transmitting) {
        audio.push(sequence, opusPayload, transmitting);
    }

    public int popPcmAudio(float[] out, int maxSamples) {
        return audio.pop(out, maxSamples);
    }

    public void moveToChannel(MumbleChannel channel) {
        if (channel == getChannel()) return;
        MumbleProto.UserState.Builder user = MumbleProto.UserState.newBuilder();
        user.setSession((int) session);
        user.setChannelId((int) channel.getChannelId());
        client.sendTcp(MumbleMessageType.USER_STATE, user.build());
    }

    public void message(String message) {
        MumbleProto.TextMessage.Builder textMessage = MumbleProto.TextMessage.newBuilder();
        textMessage.setMessage(message);
        textMessage.addSession((int) this.session);
        client.sendTcp(MumbleMessageType.TEXT_MESSAGE, textMessage.build());
    }

    public void setMute(boolean mute) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setMute(mute);
        client.sendTcp(MumbleMessageType.USER_STATE, state.build());
    }

    public void setDeaf(boolean deaf) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setDeaf(deaf);
        client.sendTcp(MumbleMessageType.USER_STATE, state.build());
    }

    public void setSelfMute(boolean mute) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setSelfMute(mute);
        client.sendTcp(MumbleMessageType.USER_STATE, state.build());
    }

    public void setSelfDeaf(boolean deaf) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setSelfDeaf(deaf);
        client.sendTcp(MumbleMessageType.USER_STATE, state.build());
    }

    public void requestStats() {
        requestStats(false);
    }

    public void requestStats(boolean statsOnly) {
        MumbleProto.UserStats.Builder stats = MumbleProto.UserStats.newBuilder();
        stats.setSession((int) this.session);
        stats.setStatsOnly(statsOnly);
        client.sendTcp(MumbleMessageType.USER_STATS, stats.build());
    }

    public boolean isAutoGainEnabled() {
        return audio.isAutoGainEnabled();
    }

    public void setAutoGainEnabled(boolean autoGainEnabled) {
        audio.setAutoGainEnabled(autoGainEnabled);
    }

    public float getManualGain() {
        return audio.getManualGain();
    }

    public void setManualGain(float manualGain) {
        audio.setManualGain(manualGain);
    }

    public String getUrl() {
        return String.format("<a href='clientid://%s' class='log-user'>%s</a>", session, name);
    }

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