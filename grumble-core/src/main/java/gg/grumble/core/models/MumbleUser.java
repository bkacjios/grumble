package gg.grumble.core.models;

import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.enums.MumbleMessageType;
import gg.grumble.core.opus.OpusDecoder;

import gg.grumble.core.utils.StringUtils;
import gg.grumble.mumble.MumbleProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static gg.grumble.core.enums.MumbleAudioConfig.*;

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
    private volatile boolean transmitting;

    private String comment;
    private String hash;

    private byte[] texture;
    private byte[] commentHash;
    private byte[] textureHash;

    private final Set<Integer> listeningChannels = new LinkedHashSet<>();

    private final TreeMap<Long, float[]> jitterBuffer = new TreeMap<>();
    private long lastPlayedSequence = -1;

    private int plcCount = 0;
    private static final byte[] EMPTY_BYTES = new byte[0];

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
        return transmitting;
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

    public void pushPcmAudio(long sequence, float[] decodedPcm, int sampleCount, boolean transmitting) {
        if (transmitting && !this.transmitting) {
            synchronized (jitterBuffer) {
                jitterBuffer.clear();
                lastPlayedSequence = sequence - 1;
            }
        }

        this.transmitting = transmitting;

        // No audio data, user is transmitting silence
        if (sampleCount <= 0) return;

        // Copy only the needed samples
        float[] pcm = Arrays.copyOf(decodedPcm, sampleCount);
        synchronized (jitterBuffer) {
            if (sequence <= lastPlayedSequence || jitterBuffer.containsKey(sequence)) {
                // Drop duplicates or too-old frames
                return;
            }
            jitterBuffer.put(sequence, pcm);
            if (jitterBuffer.size() > 10) {
                jitterBuffer.pollFirstEntry(); // avoid unbounded growth
            }
        }
    }

    public int popPcmAudio(float[] out, int maxSamples) {
        synchronized (jitterBuffer) {
            int filled = 0;

            // Pull as many in‐order frames as needed to fill maxSamples
            while (filled < maxSamples) {
                long nextSeq = lastPlayedSequence + 1;
                float[] frame = jitterBuffer.remove(nextSeq);
                if (frame == null) break;

                int toCopy = Math.min(frame.length, maxSamples - filled);
                System.arraycopy(frame, 0, out, filled, toCopy);
                filled += toCopy;

                lastPlayedSequence = nextSeq;
                plcCount = 0;
            }

            // If we got any real audio, pad the rest with silence and return full buffer
            if (filled > 0) {
                Arrays.fill(out, filled, maxSamples, 0f);
                return maxSamples;
            }

            // No queued frames → try PLC / resync / silence
            long nextSeq = lastPlayedSequence + 1;
            Map.Entry<Long, float[]> upcoming = jitterBuffer.firstEntry();
            if (upcoming != null) {
                long futureSeq = upcoming.getKey();
                long gap = futureSeq - nextSeq;
                if (gap > MAX_PLC_FRAMES) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Audio gap too large ({} frames), resynchronizing user {}", gap, name);
                    }
                    // Too big to fill with PLC → resync
                    lastPlayedSequence = futureSeq - 1;
                    plcCount = 0;
                    return popPcmAudio(out, maxSamples); // retry with adjusted sequence
                }
            }

            // Try to fill gap with PLC
            if (this.transmitting && plcCount < MAX_PLC_FRAMES) {
                plcCount++;
                lastPlayedSequence = nextSeq;

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Generated {} PLC frame", StringUtils.toOrdinal(plcCount));
                }

                OpusDecoder decoder = client.getSessionDecoder(session);
                int decoded;
                synchronized (decoder) {
                    decoded = decoder.decodeFloat(EMPTY_BYTES, out, SAMPLES_PER_FRAME);
                }
                if (decoded <= 0) {
                    Arrays.fill(out, 0, maxSamples, 0f);
                    return 0;
                }
                Arrays.fill(out, decoded, maxSamples, 0f);
                return decoded;
            }

            // Nothing to play
            Arrays.fill(out, 0, maxSamples, 0f);
            return 0;
        }
    }

    public void moveToChannel(MumbleChannel channel) {
        if (channel == getChannel()) return;
        MumbleProto.UserState.Builder user = MumbleProto.UserState.newBuilder();
        user.setSession((int) session);
        user.setChannelId((int) channel.getChannelId());
        client.send(MumbleMessageType.USER_STATE, user.build());
    }

    public void message(String message) {
        MumbleProto.TextMessage.Builder textMessage = MumbleProto.TextMessage.newBuilder();
        textMessage.setMessage(message);
        textMessage.addSession((int) this.session);
        client.send(MumbleMessageType.TEXT_MESSAGE, textMessage.build());
    }

    public void setMute(boolean mute) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setMute(mute);
        client.send(MumbleMessageType.USER_STATE, state.build());
    }

    public void setDeaf(boolean deaf) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setDeaf(deaf);
        client.send(MumbleMessageType.USER_STATE, state.build());
    }

    public void setSelfMute(boolean mute) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setSelfMute(mute);
        client.send(MumbleMessageType.USER_STATE, state.build());
    }

    public void setSelfDeaf(boolean deaf) {
        MumbleProto.UserState.Builder state = MumbleProto.UserState.newBuilder();
        state.setSession((int) this.session);
        state.setSelfDeaf(deaf);
        client.send(MumbleMessageType.USER_STATE, state.build());
    }

    public void requestStats() {
        requestStats(false);
    }

    public void requestStats(boolean statsOnly) {
        MumbleProto.UserStats.Builder stats = MumbleProto.UserStats.newBuilder();
        stats.setSession((int) this.session);
        stats.setStatsOnly(statsOnly);
        client.send(MumbleMessageType.USER_STATS, stats.build());
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