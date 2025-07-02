package gg.grumble.core.crypto;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class MumbleOCB2 {
    private int good, late, lost, resync;

    private static final int BLOCK_SIZE = 16;
    private static final int TAG_TRUNCATED = 3;

    private final BlockCipher aes = new AESEngine();
    private byte[] key;
    private final byte[] encryptIV = new byte[BLOCK_SIZE];
    private final byte[] decryptIV = new byte[BLOCK_SIZE];
    private final byte[] decryptHistory = new byte[256];
    private boolean initialized = false;

    public MumbleOCB2() {
        Arrays.fill(decryptHistory, (byte) -1);
    }

    public boolean setKey(byte[] key, byte[] clientNonce, byte[] serverNonce) {
        if (key == null || key.length != BLOCK_SIZE
                || clientNonce == null || clientNonce.length != BLOCK_SIZE
                || serverNonce == null || serverNonce.length != BLOCK_SIZE)
            return false;

        this.key = Arrays.copyOf(key, BLOCK_SIZE);
        aes.init(true, new KeyParameter(this.key));

        // clientNonce → encryptIV, serverNonce → decryptIV
        System.arraycopy(clientNonce, 0, encryptIV, 0, BLOCK_SIZE);
        System.arraycopy(serverNonce, 0, decryptIV, 0, BLOCK_SIZE);

        decryptIV[0] = (byte) ((decryptIV[0] - 1) & 0xFF);

        Arrays.fill(decryptHistory, (byte) -1);
        initialized = true;
        return true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean setDecryptIV(byte[] serverNonce) {
        if (serverNonce == null || serverNonce.length != BLOCK_SIZE) return false;
        System.arraycopy(serverNonce, 0, decryptIV, 0, BLOCK_SIZE);
        return true;
    }

    public byte[] getEncryptIV() {
        return Arrays.copyOf(encryptIV, BLOCK_SIZE);
    }

    // AES-ECB encrypt/decrypt one block
    private void aesEnc(byte[] in, byte[] out) {
        aes.init(true, new KeyParameter(key));
        aes.processBlock(in, 0, out, 0);
    }

    private void aesDec(byte[] in, byte[] out) {
        aes.init(false, new KeyParameter(key));
        aes.processBlock(in, 0, out, 0);
    }

    private static void s2(byte[] x) {
        int carry = (x[0] & 0xFF) >>> 7;
        for (int i = 0; i < BLOCK_SIZE - 1; i++) {
            x[i] = (byte) (((x[i] & 0xFF) << 1) | ((x[i + 1] & 0xFF) >>> 7));
        }
        x[BLOCK_SIZE - 1] = (byte) (((x[BLOCK_SIZE - 1] & 0xFF) << 1) ^ (carry * 0x87));
    }

    private static void s3(byte[] x) {
        byte[] tmp = x.clone();
        s2(tmp);
        for (int i = 0; i < BLOCK_SIZE; i++) x[i] ^= tmp[i];
    }

    public byte[] encrypt(byte[] plain) {
        byte[] out = new byte[4 + plain.length];
        byte[] ciphertext = new byte[plain.length];
        byte[] tag = new byte[BLOCK_SIZE];

        // increment IV once
        incIV(encryptIV);
        ocbEncrypt(plain, 0, plain.length, encryptIV, ciphertext, tag);

        out[0] = encryptIV[0];
        System.arraycopy(tag, 0, out, 1, TAG_TRUNCATED);
        System.arraycopy(ciphertext, 0, out, 4, ciphertext.length);
        return out;
    }

    public byte[] encrypt(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.mark();
        buffer.get(data);
        buffer.reset();
        return encrypt(data);
    }

    private boolean ocbEncrypt(byte[] plain, int off, int len, byte[] nonce,
                               byte[] out, byte[] tag) {
        byte[] delta = new byte[BLOCK_SIZE];
        byte[] checksum = new byte[BLOCK_SIZE];
        byte[] tmp = new byte[BLOCK_SIZE];
        byte[] pad = new byte[BLOCK_SIZE];

        // L₀ = AES-ENC(nonce)
        aesEnc(Arrays.copyOf(nonce, BLOCK_SIZE), delta);
        Arrays.fill(checksum, (byte) 0);

        int pos = off, outPos = 0, rem = len;
        // full blocks
        while (rem > BLOCK_SIZE) {
            s2(delta);
            for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] = (byte) (delta[i] ^ plain[pos + i]);
            aesEnc(tmp, tmp);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                out[outPos + i] = (byte) (delta[i] ^ tmp[i]);
                checksum[i] ^= plain[pos + i];
            }
            rem -= BLOCK_SIZE;
            pos += BLOCK_SIZE;
            outPos += BLOCK_SIZE;
        }

        // final partial
        s2(delta);
        // build pad
        Arrays.fill(tmp, (byte) 0);
        ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN)
                .putInt(BLOCK_SIZE - Integer.BYTES, rem * 8);
        for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] ^= delta[i];
        aesEnc(tmp, pad);

        // ─── checksum sees raw plaintext (and pad tail) ───
        byte[] tmpBlk = new byte[BLOCK_SIZE];
        for (int i = 0; i < rem; i++) tmpBlk[i] = plain[pos + i];
        for (int i = rem; i < BLOCK_SIZE; i++) tmpBlk[i] = pad[i];
        for (int i = 0; i < BLOCK_SIZE; i++) checksum[i] ^= tmpBlk[i];

        // produce ciphertext bytes
        for (int i = 0; i < rem; i++) {
            out[outPos + i] = (byte) (plain[pos + i] ^ pad[i]);
        }

        // final tag: AES-ENC( Δ⋆3 ⊕ checksum )
        s3(delta);
        for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] = (byte) (delta[i] ^ checksum[i]);
        aesEnc(tmp, tag);

        return true;
    }

    public byte[] decrypt(byte[] packet) {
        byte[] saveIV = Arrays.copyOf(decryptIV, BLOCK_SIZE);

        if (packet == null || packet.length < 4) {
            // too short or null → no change
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            return null;
        }

        int iv = packet[0] & 0xFF;
        int cur = decryptIV[0] & 0xFF;
        int lateCount = 0;
        int lostCount = 0;
        boolean restore = false;

        // in‐order?
        if (((cur + 1) & 0xFF) == iv) {
            if (iv > cur) {
                // normal advance
                decryptIV[0] = (byte) iv;
            } else {
                // wrapped around
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    if (++decryptIV[i] != 0) break;
                }
            }
        }
        // duplicate packet?
        else if (iv == cur) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            return null;
        }
        // out‐of‐order / lost / wrapped / late
        else {
            int diff = iv - cur;
            if (diff > 128) diff -= 256;
            if (diff < -128) diff += 256;

            // late but within window (no wrap)
            if ((iv < cur) && diff > -30 && diff < 0) {
                lateCount = 1;
                lostCount = -1;
                decryptIV[0] = (byte) iv;
                restore = true;
            }
            // late with wraparound decrement
            else if ((iv > cur) && diff > -30 && diff < 0) {
                lateCount = 1;
                lostCount = -1;
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    int old = decryptIV[i] & 0xFF;
                    decryptIV[i] = (byte) ((old - 1) & 0xFF);
                    if (old != 0) break;
                }
                restore = true;
            }
            else if (diff > 0) {
                // lost forward
                lostCount = diff - 1;
                decryptIV[0] = (byte) iv;
            } else {
                // lost with wrap
                lostCount = 256 - cur + iv - 1;
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    if (++decryptIV[i] != 0) break;
                }
            }
        }

        // replay check
        int idx = decryptIV[0] & 0xFF;
        int decVal = decryptIV[1] & 0xFF;
        if (decryptHistory[idx] == (byte) decVal) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            return null;
        }

        // peel off tag + ciphertext
        byte[] tagBytes = Arrays.copyOfRange(packet, 1, 1 + TAG_TRUNCATED);
        byte[] cipher = Arrays.copyOfRange(packet, 4, packet.length);

        byte[] plain = new byte[cipher.length];
        boolean ok = ocbDecrypt(cipher, 0, cipher.length, decryptIV, plain, tagBytes);
        if (!ok) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            return null;
        }

        // stats
        decryptHistory[idx] = (byte) decVal;
        good++;
        late += lateCount;
        lost = Math.max(0, lost + lostCount);
        if (restore) {
            // exact match to C++: restore IV *then* count resync
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            resync++;
        }

        return plain;
    }

    private boolean ocbDecrypt(byte[] cipher, int off, int len,
                               byte[] nonce, byte[] out, byte[] tag) {
        byte[] delta = new byte[BLOCK_SIZE];
        byte[] checksum = new byte[BLOCK_SIZE];
        byte[] tmp = new byte[BLOCK_SIZE];
        byte[] pad = new byte[BLOCK_SIZE];

        aesEnc(Arrays.copyOf(nonce, BLOCK_SIZE), delta);
        Arrays.fill(checksum, (byte) 0);

        int pos = off, outPos = 0, rem = len;
        while (rem > BLOCK_SIZE) {
            s2(delta);
            for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] = (byte) (delta[i] ^ cipher[pos + i]);
            aesDec(tmp, tmp);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                out[outPos + i] = (byte) (delta[i] ^ tmp[i]);
                checksum[i] ^= out[outPos + i];
            }
            rem -= BLOCK_SIZE;
            pos += BLOCK_SIZE;
            outPos += BLOCK_SIZE;
        }

        s2(delta);
        Arrays.fill(tmp, (byte) 0);
        ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN)
                .putInt(BLOCK_SIZE - 4, rem * 8);
        for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] ^= delta[i];
        aesEnc(tmp, pad);

        byte[] fullBlk = new byte[BLOCK_SIZE];
        for (int i = 0; i < rem; i++) fullBlk[i] = (byte) (cipher[pos + i] ^ pad[i]);
        for (int i = rem; i < BLOCK_SIZE; i++) fullBlk[i] = pad[i];
        for (int i = 0; i < BLOCK_SIZE; i++) checksum[i] ^= fullBlk[i];

        if (Arrays.equals(
                Arrays.copyOf(fullBlk, BLOCK_SIZE - 1),
                Arrays.copyOf(delta, BLOCK_SIZE - 1))) {
            return false;
        }

        System.arraycopy(fullBlk, 0, out, outPos, rem);

        s3(delta);
        for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] = (byte) (delta[i] ^ checksum[i]);
        aesEnc(tmp, pad);
        for (int i = 0; i < TAG_TRUNCATED; i++) {
            if (pad[i] != tag[i]) return false;
        }
        return true;
    }

    private static void incIV(byte[] iv) {
        for (int i = 0; i < BLOCK_SIZE; i++) {
            iv[i] = (byte) ((iv[i] + 1) & 0xFF);
            if (iv[i] != 0) break;
        }
    }

    public int getGood() {
        return good;
    }

    public int getLate() {
        return late;
    }

    public int getLost() {
        return lost;
    }

    public int getResync() {
        return resync;
    }
}
