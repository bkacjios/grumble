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
    private final short[] decryptHistory = new short[256];
    private boolean initialized = false;

    public static class DecryptException extends Exception {
        public DecryptException(String message) {
            super(message);
        }
    }

    public MumbleOCB2() {
        Arrays.fill(decryptHistory, (short) -1);
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

        Arrays.fill(decryptHistory, (short) -1);
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
        ocbEncrypt(plain, plain.length, encryptIV, ciphertext, tag);

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

    private void ocbEncrypt(byte[] plain, int len, byte[] nonce,
                            byte[] out, byte[] tag) {
        byte[] delta = new byte[BLOCK_SIZE];
        byte[] checksum = new byte[BLOCK_SIZE];
        byte[] tmp = new byte[BLOCK_SIZE];
        byte[] pad = new byte[BLOCK_SIZE];

        // L₀ = AES-ENC(nonce)
        aesEnc(Arrays.copyOf(nonce, BLOCK_SIZE), delta);
        Arrays.fill(checksum, (byte) 0);

        int pos = 0, outPos = 0, rem = len;
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
        if (rem >= 0) System.arraycopy(plain, pos, tmpBlk, 0, rem);
        if (BLOCK_SIZE - rem >= 0) System.arraycopy(pad, rem, tmpBlk, rem, BLOCK_SIZE - rem);
        for (int i = 0; i < BLOCK_SIZE; i++) checksum[i] ^= tmpBlk[i];

        // produce ciphertext bytes
        for (int i = 0; i < rem; i++) {
            out[outPos + i] = (byte) (plain[pos + i] ^ pad[i]);
        }

        // final tag: AES-ENC( Δ⋆3 ⊕ checksum )
        s3(delta);
        for (int i = 0; i < BLOCK_SIZE; i++) tmp[i] = (byte) (delta[i] ^ checksum[i]);
        aesEnc(tmp, tag);

    }

    public byte[] decrypt(byte[] packet) throws DecryptException {
        byte[] saveIV = Arrays.copyOf(decryptIV, BLOCK_SIZE);
        int lateCount = 0;
        int lostCount = 0;
        boolean restore = false;

        if (packet == null || packet.length < 4) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            throw new DecryptException("Packet is null or too short to decrypt");
        }

        int iv = packet[0] & 0xFF;
        int cur = decryptIV[0] & 0xFF;

        // normalize into –128..127
        int diff = iv - cur;
        if (diff > 128) diff -= 256;
        else if (diff < -128) diff += 256;

        if (diff == 1) {
            if (iv > cur) {
                decryptIV[0] = (byte) iv;
            } else {
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    if (++decryptIV[i] != 0) break;
                }
            }
        } else if (diff == 0) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            throw new DecryptException("Duplicate packet (IV already used)");
        } else if (diff < 0) {
            if ((iv < cur) && (diff > -30)) {
                lateCount = 1;
                lostCount = -1;
                decryptIV[0] = (byte) iv;
                restore = true;
            } else if ((iv > cur) && (diff > -30)) {
                lateCount = 1;
                lostCount = -1;
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    int old = decryptIV[i] & 0xFF;
                    decryptIV[i] = (byte) ((old - 1) & 0xFF);
                    if (old != 0) break;
                }
                restore = true;
            } else {
                lostCount = 256 - cur + iv - 1;
                decryptIV[0] = (byte) iv;
                for (int i = 1; i < BLOCK_SIZE; i++) {
                    if (++decryptIV[i] != 0) break;
                }
            }
        } else {
            lostCount = diff - 1;
            decryptIV[0] = (byte) iv;
        }

        int idx = decryptIV[0] & 0xFF;
        short fullVal = ByteBuffer.wrap(decryptIV, 1, 2).order(ByteOrder.BIG_ENDIAN).getShort();

        if (decryptHistory[idx] == fullVal) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            System.err.printf("REPLAY: idx=%d fullVal=%04x decryptIV=%s\n",
                    idx, fullVal & 0xFFFF, Arrays.toString(decryptIV));
            throw new DecryptException("Replay detected: IV already used at this index");
        }

        byte[] tagBytes = Arrays.copyOfRange(packet, 1, 1 + TAG_TRUNCATED);
        byte[] cipher = Arrays.copyOfRange(packet, 4, packet.length);
        byte[] plain = new byte[cipher.length];
        if (!ocbDecrypt(cipher, cipher.length, decryptIV, plain, tagBytes)) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            throw new DecryptException("OCB decryption failed: authentication tag mismatch or invalid cipher");
        }

        if (!restore) {
            decryptHistory[idx] = fullVal;
        }
        good++;
        late += lateCount;
        if (lostCount > 0) {
            lost += lostCount;
        } else if (lost > -lostCount) {
            lost += lostCount;
        }

        if (restore) {
            System.arraycopy(saveIV, 0, decryptIV, 0, BLOCK_SIZE);
            resync++;
        }

        return plain;
    }

    private boolean ocbDecrypt(byte[] cipher, int len,
                               byte[] nonce, byte[] out, byte[] tag) {
        byte[] delta = new byte[BLOCK_SIZE];
        byte[] checksum = new byte[BLOCK_SIZE];
        byte[] tmp = new byte[BLOCK_SIZE];
        byte[] pad = new byte[BLOCK_SIZE];

        aesEnc(Arrays.copyOf(nonce, BLOCK_SIZE), delta);
        Arrays.fill(checksum, (byte) 0);

        int pos = 0, outPos = 0, rem = len;
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
        if (BLOCK_SIZE - rem >= 0) System.arraycopy(pad, rem, fullBlk, rem, BLOCK_SIZE - rem);
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
