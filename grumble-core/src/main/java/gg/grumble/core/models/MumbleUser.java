package gg.grumble.core.models;

import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.enums.MumbleMessageType;
import gg.grumble.core.utils.FloatRingBuffer;
import gg.grumble.mumble.MumbleProto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static gg.grumble.core.enums.MumbleAudioConfig.*;

public class MumbleUser {
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
    private boolean prioritySpeaker;
    private boolean recording;
    private boolean speaking;

    private String comment;
    private String hash;

    private byte[] texture;
    private byte[] commentHash;
    private byte[] textureHash;

    private final Set<Integer> listeningChannels = new LinkedHashSet<>();

    private final FloatRingBuffer pcmBuffer = new FloatRingBuffer(SAMPLE_RATE * CHANNELS, JITTER_THRESHOLD);

    private boolean autoGainEnabled = false;
    private float manualGain = 1.0f;

    public MumbleUser(MumbleClient client, long session) {
        this.client = client;
        this.session = session;
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

    public boolean isPrioritySpeaker() {
        return prioritySpeaker;
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isSpeaking() {
        return speaking;
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

    public void pushPcmAudio(float[] decodedPcm, int sampleCount, boolean speaking) {
        this.speaking = speaking;
        pcmBuffer.write(decodedPcm, 0, sampleCount);
    }

    public int popPcmAudio(float[] out, int maxSamples) {
        return pcmBuffer.read(out, 0, maxSamples);
    }

    public void moveToChannel(MumbleChannel channel) {
        if (channel == getChannel()) return;
        MumbleProto.UserState.Builder user = MumbleProto.UserState.newBuilder();
        user.setSession((int) session);
        user.setChannelId((int) channel.getChannelId());
        client.send(MumbleMessageType.USER_STATE, user.build());
    }

    public boolean isAutoGainEnabled() {
        return autoGainEnabled;
    }

    public void setAutoGainEnabled(boolean autoGainEnabled) {
        this.autoGainEnabled = autoGainEnabled;
    }

    public float getManualGain() {
        return manualGain;
    }

    public void setManualGain(float manualGain) {
        this.manualGain = manualGain;
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