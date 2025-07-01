package gg.grumble.core.client;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.Security;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class MumbleCrypto {

    private static final int AES_KEY_SIZE = 16; // 128-bit key
    private static final int AES_BLOCK_SIZE = 16;
    private static final int FULL_TAG_SIZE = 16;
    private static final int TAG_SIZE = 3;

    private final byte[] encryptIV = new byte[AES_BLOCK_SIZE];
    private final byte[] decryptIV = new byte[AES_BLOCK_SIZE];
    private final byte[] decryptHistory = new byte[256]; // For replay protection

    private final Cipher encryptCipher;
    private final Cipher decryptCipher;
    private final SecretKeySpec secretKeySpec;

    private boolean initialized = false;

    // Tracking counters like Mumble
    private int good = 0;
    private int late = 0;
    private int lost = 0;
    private int resync = 0;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public boolean isValid() {
        return initialized;
    }

    public MumbleCrypto() {
        try {
            SecureRandom rng = new SecureRandom();

            byte[] key = new byte[AES_KEY_SIZE];

            rng.nextBytes(key);
            rng.nextBytes(encryptIV);
            rng.nextBytes(decryptIV);

            secretKeySpec = new SecretKeySpec(key, "AES");
            encryptCipher = Cipher.getInstance("AES/OCB/NoPadding", "BC");
            decryptCipher = Cipher.getInstance("AES/OCB/NoPadding", "BC");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MumbleCrypto", e);
        }
    }

    public boolean setKey(byte[] key, byte[] clientNonce, byte[] serverNonce) {
        if (key.length != AES_KEY_SIZE || clientNonce.length != AES_BLOCK_SIZE || serverNonce.length != AES_BLOCK_SIZE)
            return false;

        System.arraycopy(key, 0, secretKeySpec.getEncoded(), 0, AES_KEY_SIZE); // Optional, since already set
        System.arraycopy(clientNonce, 0, encryptIV, 0, AES_BLOCK_SIZE);
        System.arraycopy(serverNonce, 0, decryptIV, 0, AES_BLOCK_SIZE);

        initialized = true;
        return true;
    }

    public boolean setDecryptIV(byte[] iv) {
        if (iv.length != AES_BLOCK_SIZE)
            return false;

        System.arraycopy(iv, 0, decryptIV, 0, AES_BLOCK_SIZE);
        return true;
    }

    public byte[] getEncryptIV() {
        return Arrays.copyOf(encryptIV, AES_BLOCK_SIZE);
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        // Increment first byte of IV (wraps naturally)
        for (int i = 0; i < AES_BLOCK_SIZE; i++) {
            if (++encryptIV[i] != 0) break;
        }

        IvParameterSpec ivSpec = new IvParameterSpec(encryptIV);
        encryptCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);

        byte[] fullOutput = encryptCipher.doFinal(plaintext);
        int clen = fullOutput.length - FULL_TAG_SIZE;
        byte[] tag = new byte[TAG_SIZE];
        System.arraycopy(fullOutput, clen, tag, 0, TAG_SIZE);

        // Construct packet: [ivByte][tag3][tag2][tag1][ciphertext...]
        byte[] packet = new byte[1 + TAG_SIZE + clen];
        packet[0] = encryptIV[0];
        System.arraycopy(tag, 0, packet, 1, TAG_SIZE);
        System.arraycopy(fullOutput, 0, packet, 1 + TAG_SIZE, clen);
        return packet;
    }

    public byte[] decrypt(byte[] packet) throws Exception {
        if (packet.length < 4) return null;

        byte ivByte = packet[0];
        byte[] tag = new byte[TAG_SIZE];
        System.arraycopy(packet, 1, tag, 0, TAG_SIZE);
        byte[] ciphertext = new byte[packet.length - 4];
        System.arraycopy(packet, 4, ciphertext, 0, ciphertext.length);

        byte[] saveIV = decryptIV.clone();
        int lostCount = 0;
        int lateCount = 0;

        int diff = (ivByte - decryptIV[0] + 256) % 256;

        if (((decryptIV[0] + 1) & 0xFF) == (ivByte & 0xFF)) {
            decryptIV[0] = ivByte;
        } else if (diff > 0 && diff < 128) {
            lostCount = diff - 1;
            decryptIV[0] = ivByte;
        } else if (diff > 128 && diff < 256 - 30) {
            lateCount = 1;
            decryptIV[0] = ivByte;
        } else {
            resync++;
            return null;
        }

        if (decryptHistory[ivByte & 0xFF] == decryptIV[1]) {
            resync++;
            return null;
        }

        IvParameterSpec ivSpec = new IvParameterSpec(decryptIV);
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);

        byte[] fullOutput;
        try {
            fullOutput = decryptCipher.doFinal(ciphertext);
        } catch (Exception e) {
            resync++;
            System.arraycopy(saveIV, 0, decryptIV, 0, AES_BLOCK_SIZE);
            return null;
        }

        byte[] expectedTag = new byte[TAG_SIZE];
        System.arraycopy(fullOutput, fullOutput.length - FULL_TAG_SIZE, expectedTag, 0, TAG_SIZE);

        if (!constantTimeEquals(expectedTag, tag)) {
            resync++;
            System.arraycopy(saveIV, 0, decryptIV, 0, AES_BLOCK_SIZE);
            return null;
        }

        decryptHistory[decryptIV[0] & 0xFF] = decryptIV[1];
        good++;
        late += lateCount;
        lost += lostCount;

        return java.util.Arrays.copyOf(fullOutput, fullOutput.length - FULL_TAG_SIZE);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) result |= a[i] ^ b[i];
        return result == 0;
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
