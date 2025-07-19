package gg.grumble.core.utils;

public final class AudioUtils {
    private AudioUtils() {
    }

    public static short[] bytesToShorts(byte[] bytes) {
        int samples = bytes.length / 2;          // 1920 bytes → 960 samples
        short[] pcm = new short[samples];
        for (int i = 0, j = 0; i < samples; i++) {
            int lo = bytes[j++] & 0xFF;          // low  byte
            int hi = bytes[j++] << 8;            // high byte (signed shift)
            pcm[i] = (short) (hi | lo);
        }
        return pcm;
    }

    public static byte[] shortsToBytes(short[] shorts) {
        byte[] pcm = new byte[shorts.length * 2];  // 960 samples → 1920 bytes
        for (int i = 0, j = 0; i < shorts.length; i++) {
            short s = shorts[i];
            pcm[j++] = (byte) s;                   // low byte
            pcm[j++] = (byte)(s >>> 8);            // high byte, unsigned shift
        }
        return pcm;
    }
}
