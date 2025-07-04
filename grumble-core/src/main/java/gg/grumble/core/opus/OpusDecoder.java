package gg.grumble.core.opus;

import com.sun.jna.ptr.PointerByReference;
import tomp2p.opuswrapper.Opus;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class OpusDecoder {

    private final PointerByReference decoder;
    private final int channels;

    public OpusDecoder(int sampleRate, int channels) {
        this.channels = channels;

        var err = java.nio.IntBuffer.allocate(1);
        var dec = Opus.INSTANCE.opus_decoder_create(sampleRate, channels, err);
        if (err.get(0) != Opus.OPUS_OK) {
            throw new OpusException("Failed to create decoder: " + Opus.INSTANCE.opus_strerror(err.get(0)));
        }
        this.decoder = dec;
    }

    public int decode(byte[] encoded, short[] pcm, int frameSize) {
        return decode(encoded, pcm, frameSize, false);
    }

    public int decode(byte[] encoded, short[] pcm, int frameSize, boolean fec) {
        ShortBuffer pcmBuf = ShortBuffer.wrap(pcm);
        int result = Opus.INSTANCE.opus_decode(decoder, encoded, encoded.length, pcmBuf, frameSize, fec ? 1 : 0);
        if (result < 0) {
            throw new OpusException("Decoding failed: " + Opus.INSTANCE.opus_strerror(result));
        }
        return result * channels;
    }

    public int decodeFloat(byte[] encoded, float[] pcm, int frameSize) {
        return decodeFloat(encoded, pcm, frameSize, false);
    }

    public int decodeFloat(byte[] encoded, float[] pcm, int frameSize, boolean fec) {
        FloatBuffer pcmBuf = FloatBuffer.wrap(pcm);
        int result = Opus.INSTANCE.opus_decode_float(decoder, encoded, encoded.length, pcmBuf, frameSize, fec ? 1 : 0);
        if (result < 0) {
            throw new OpusException("Decoding failed: " + Opus.INSTANCE.opus_strerror(result));
        }
        return result * channels;
    }

    /**
     * Wrap opus_decoder_get_nb_samples to query the number of samples in a packet.
     * @param data the encoded opus packet
     * @return number of samples per channel in this packet
     */
    public int getNbSamples(byte[] data) {
        int nbSamples = Opus.INSTANCE.opus_decoder_get_nb_samples(decoder, data, data.length);
        if (nbSamples < 0) {
            throw new OpusException("opus_decoder_get_nb_samples failed: " + Opus.INSTANCE.opus_strerror(nbSamples));
        }
        return nbSamples;
    }

    public void destroy() {
        if (decoder != null) {
            Opus.INSTANCE.opus_decoder_destroy(decoder);
        }
    }

    public PointerByReference getNative() {
        return decoder;
    }
}
