package gg.grumble.core.audio;

import gg.grumble.core.models.MumbleUser;
import gg.grumble.core.opus.OpusDecoder;
import gg.grumble.core.opus.OpusException;
import gg.grumble.core.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static gg.grumble.core.enums.MumbleAudioConfig.*;

/**
 * Per-user playback pipeline: buffers raw Opus frames from the network keyed by sequence
 * number to smooth out jitter and reorder them, decodes them in that strict sequence
 * order on pop (Opus decoding is stateful, so decoding out of order corrupts the decoder),
 * and generates packet-loss-concealment (PLC) frames via the user's Opus decoder when
 * real frames don't arrive in time.
 */
public class UserAudioBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(UserAudioBuffer.class);
    private static final float[] EMPTY_FLOATS = new float[0];
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final MumbleUser user;

    private final TreeMap<Long, byte[]> jitterBuffer = new TreeMap<>();
    private long lastPlayedSequence = -1;
    private volatile boolean transmitting;

    private long jitterPrefillStartTime = 0;
    private boolean jitterReady = false;
    private int plcCount = 0;

    // PCM left over from a decoded frame that didn't fully fit in the previous pop().
    private float[] leftoverPcm = null;
    private int leftoverOffset = 0;

    // Per-channel sample count of the last successfully decoded real frame, so PLC
    // concealment requests a duration consistent with the stream instead of assuming 20ms.
    private int lastFrameSize = SAMPLES_PER_FRAME;

    private boolean autoGainEnabled = false;
    private float manualGain = 1.0f;

    public UserAudioBuffer(MumbleUser user) {
        this.user = user;
    }

    public boolean isTransmitting() {
        return transmitting;
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

    /**
     * Queue a raw Opus frame from the network for playback. May arrive out of order;
     * decoding is deferred to {@link #pop} so frames are always decoded in sequence order.
     */
    public void push(long sequence, byte[] opusPayload, boolean transmitting) {
        if (transmitting && !this.transmitting) {
            synchronized (jitterBuffer) {
                jitterBuffer.clear();
                lastPlayedSequence = sequence - 1;
                leftoverPcm = null;
                leftoverOffset = 0;
                plcCount = 0;
                // Re-prefill for every new talk spurt, not just the first one ever seen -
                // otherwise playback starts straight into PLC concealment instead of
                // waiting for a few real frames to arrive.
                jitterReady = false;
                jitterPrefillStartTime = 0;
            }
        }

        this.transmitting = transmitting;

        if (opusPayload.length == 0) return;

        synchronized (jitterBuffer) {
            long age = lastPlayedSequence - sequence;
            if (age > JITTER_MAX_PLC_FRAMES) {
                LOG.warn("Dropping frame: {} frames late", age);
                return;
            }

            if (jitterBuffer.containsKey(sequence)) {
                LOG.warn("Dropping duplicate frame: {}", sequence);
                return;
            }
            jitterBuffer.put(sequence, opusPayload);

            while (!jitterBuffer.isEmpty() &&
                    jitterBuffer.firstKey() <= (lastPlayedSequence - JITTER_MAX_PLC_FRAMES)) {
                jitterBuffer.pollFirstEntry(); // evict unusable frames
            }

            if (jitterBuffer.size() > JITTER_MAX_TOTAL_FRAMES) {
                jitterBuffer.pollFirstEntry(); // prevent runaway growth
            }
        }
    }

    /** Pop up to maxSamples of PCM for playback, filling gaps with PLC or silence. */
    public int pop(float[] out, int maxSamples) {
        synchronized (jitterBuffer) {
            long nextSeq = lastPlayedSequence + 1;

            // Initial jitter prefill
            if (!jitterReady) {
                if (jitterPrefillStartTime == 0) {
                    jitterPrefillStartTime = System.currentTimeMillis();
                }

                int available = 0;
                long seq = nextSeq;
                while (jitterBuffer.containsKey(seq++) && available < JITTER_PREFILL_FRAMES) {
                    available++;
                }

                long waited = System.currentTimeMillis() - jitterPrefillStartTime;
                boolean gotFullPrefill = available >= JITTER_PREFILL_FRAMES;
                // After the normal prefill window, start with whatever real audio has
                // shown up - but never with zero frames. PLC conceals FROM a real decoded
                // frame; with none yet it has nothing to extrapolate and comes out garbled
                // instead of silent, which is what caused stutter at the start of playback.
                boolean settleForWhatArrived = waited > JITTER_PREFILL_TIMEOUT_MS && available >= 1;
                boolean abandoned = waited > JITTER_PREFILL_ABANDON_MS;

                if (gotFullPrefill || settleForWhatArrived || abandoned) {
                    jitterReady = true;
                } else {
                    Arrays.fill(out, 0, maxSamples, 0f);
                    return 0;
                }
            }

            int filled = 0;

            if (leftoverPcm != null) {
                filled = takeLeftover(out, maxSamples);
            }

            // Playback from jitter buffer, decoding each frame in strict sequence order
            while (filled < maxSamples) {
                byte[] payload = jitterBuffer.remove(nextSeq);
                if (payload == null) break;

                float[] pcm = decode(nextSeq, payload);
                lastPlayedSequence = nextSeq;
                nextSeq++;

                // PLC concealment itself only logs at DEBUG (too noisy at INFO if it's
                // legitimately rare), but frequent small losses are the more insidious
                // case - each one alone looks harmless, so surface it here whenever a
                // real frame ends a concealment run, at whatever level actually shows.
                if (plcCount > 0 && LOG.isDebugEnabled()) {
                    LOG.debug("Recovered after concealing {} frame(s) for user {}", plcCount, user.getName());
                }
                plcCount = 0;

                if (pcm.length == 0) continue; // decoder produced nothing for this frame

                int toCopy = Math.min(pcm.length, maxSamples - filled);
                System.arraycopy(pcm, 0, out, filled, toCopy);
                filled += toCopy;

                if (toCopy < pcm.length) {
                    leftoverPcm = pcm;
                    leftoverOffset = toCopy;
                    break; // keep same nextSeq semantics: rest is served from leftover next call
                }
            }

            // If we got any real audio, pad the rest with silence and return full buffer
            if (filled > 0) {
                Arrays.fill(out, filled, maxSamples, 0f);
                return maxSamples;
            }

            // Check for a future frame
            Map.Entry<Long, byte[]> upcoming = jitterBuffer.firstEntry();
            if (upcoming != null) {
                long futureSeq = upcoming.getKey();
                long gap = futureSeq - nextSeq;
                if (gap > JITTER_MAX_PLC_FRAMES) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Audio gap too large ({} frames), resynchronizing user {}", gap, user.getName());
                    }
                    // Too big to fill with PLC → resync
                    lastPlayedSequence = futureSeq - 1;
                    plcCount = 0;
                    return pop(out, maxSamples); // retry with adjusted sequence
                }
            }

            // Try to fill gap with PLC
            if (this.transmitting && plcCount < JITTER_MAX_PLC_FRAMES) {
                plcCount++;
                lastPlayedSequence = nextSeq;

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Generated {} PLC frame", StringUtils.toOrdinal(plcCount));
                }

                OpusDecoder decoder = user.getClient().getSessionDecoder(user.getSession());
                int decoded;
                synchronized (decoder) {
                    try {
                        decoded = decoder.decodeFloat(EMPTY_BYTES, out, lastFrameSize);
                    } catch (OpusException e) {
                        LOG.warn("PLC concealment failed for user {}: {}", user.getName(), e.getMessage());
                        decoded = -1;
                    }
                }

                if (decoded <= 0) {
                    LOG.warn("PLC concealment produced no samples for user {} (frameSize={})", user.getName(), lastFrameSize);
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

    private int takeLeftover(float[] out, int maxSamples) {
        int available = leftoverPcm.length - leftoverOffset;
        int toCopy = Math.min(available, maxSamples);
        System.arraycopy(leftoverPcm, leftoverOffset, out, 0, toCopy);
        leftoverOffset += toCopy;
        if (leftoverOffset >= leftoverPcm.length) {
            leftoverPcm = null;
            leftoverOffset = 0;
        }
        return toCopy;
    }

    private float[] decode(long sequence, byte[] payload) {
        OpusDecoder decoder = user.getClient().getSessionDecoder(user.getSession());
        synchronized (decoder) {
            int frameSize;
            float[] pcm;
            int decoded;
            try {
                frameSize = decoder.getNbSamples(payload);
                pcm = new float[frameSize * CHANNELS];
                decoded = decoder.decodeFloat(payload, pcm, frameSize);
            } catch (OpusException e) {
                // A single malformed/corrupt packet must not propagate - it's caught all
                // the way up in MumbleClient's mix loop and would blank every user's audio
                // for the tick, not just this one frame.
                LOG.warn("Failed to decode frame {} for user {}: {}", sequence, user.getName(), e.getMessage());
                return EMPTY_FLOATS;
            }

            if (decoded <= 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to decode frame {} for user {}", sequence, user.getName());
                }
                return EMPTY_FLOATS;
            }
            lastFrameSize = frameSize;
            return decoded == pcm.length ? pcm : Arrays.copyOf(pcm, decoded);
        }
    }
}
