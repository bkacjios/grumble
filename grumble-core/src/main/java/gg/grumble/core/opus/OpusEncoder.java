package gg.grumble.core.opus;

import com.sun.jna.ptr.PointerByReference;
import tomp2p.opuswrapper.Opus;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;

public class OpusEncoder {
    private final PointerByReference encoder;

    public OpusEncoder(int sampleRate, int channels, int application) {
        encoder = new PointerByReference();
        var errorBuf = java.nio.IntBuffer.allocate(1);
        var enc = Opus.INSTANCE.opus_encoder_create(sampleRate, channels, application, errorBuf);
        if (errorBuf.get(0) != Opus.OPUS_OK) {
            throw new OpusException("Failed to create encoder: " + Opus.INSTANCE.opus_strerror(errorBuf.get(0)));
        }
        this.encoder.setPointer(enc.getPointer());
    }

    public int encode(short[] pcm, int frameSize, byte[] output) {
        Objects.requireNonNull(pcm);
        Objects.requireNonNull(output);

        ShortBuffer pcmBuf = ShortBuffer.wrap(pcm);
        ByteBuffer outBuf = ByteBuffer.wrap(output);

        int encoded = Opus.INSTANCE.opus_encode(encoder, pcmBuf, frameSize, outBuf, output.length);
        if (encoded < 0) {
            throw new OpusException("Encoding failed: " + Opus.INSTANCE.opus_strerror(encoded));
        }
        return encoded;
    }

    public void destroy() {
        if (encoder != null) {
            Opus.INSTANCE.opus_encoder_destroy(encoder);
        }
    }

    public void setBitrate(int bitrate) {
        Opus.INSTANCE.opus_encoder_ctl(encoder, Opus.OPUS_SET_BITRATE_REQUEST, bitrate);
    }

    public void setComplexity(int complexity) {
        Opus.INSTANCE.opus_encoder_ctl(encoder, Opus.OPUS_SET_COMPLEXITY_REQUEST, complexity);
    }

    public void setInbandFEC(boolean enabled) {
        Opus.INSTANCE.opus_encoder_ctl(encoder, Opus.OPUS_SET_INBAND_FEC_REQUEST, enabled ? 1 : 0);
    }

    public PointerByReference getNative() {
        return encoder;
    }
}