package gg.grumble.core.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MumbleOCB2Test {
    /**
     * Known keys and IV's that result in our "Hello world!" being encoded with expected values.
     */
    private static final String KEY_HEX        = "00112233445566778899aabbccddeeff";
    private static final String CLIENT_IV_HEX  = "202122232425262728292a2b2c2d2e2f";
    private static final String SERVER_IV_HEX  = "101112131415161718191a1b1c1d1e1f";
    /**
     * "Hello world!" should result in these expected values on a REAL mumble client.
     */
    private static final String TEST_MESSAGE   = "Hello world!";
    private static final String EXPECTED_C2S   = "114074199b4e20b7537da596e6060611";
    private static final String EXPECTED_S2C   = "210d0ebfeec286d7ebdb9d1698852515";

    @Test
    void testClientToServer() throws MumbleOCB2.DecryptException {
        byte[] key      = hexToBytes(KEY_HEX);
        byte[] clientIV = hexToBytes(CLIENT_IV_HEX);
        byte[] serverIV = hexToBytes(SERVER_IV_HEX);

        // client encrypts with clientIV, decrypts with serverIV
        MumbleOCB2 client = new MumbleOCB2();
        assertTrue(client.setKey(key, clientIV, serverIV), "client:setKey failed");

        // server decrypts with clientIV, encrypts with serverIV
        MumbleOCB2 server = new MumbleOCB2();
        assertTrue(server.setKey(key, serverIV, clientIV), "server:setKey failed");

        // CLIENT → SERVER
        byte[] ct = client.encrypt(TEST_MESSAGE.getBytes());
        assertNotNull(ct, "encryption returned null");
        assertEquals(EXPECTED_S2C, bytesToHex(ct), "client→server ciphertext");

        byte[] pt = server.decrypt(ct);
        assertNotNull(pt, "decryption returned null on server");
        assertEquals(TEST_MESSAGE, new String(pt), "server decrypted plaintext");
    }

    @Test
    void testServerToClient() throws MumbleOCB2.DecryptException {
        byte[] key      = hexToBytes(KEY_HEX);
        byte[] clientIV = hexToBytes(CLIENT_IV_HEX);
        byte[] serverIV = hexToBytes(SERVER_IV_HEX);

        // same setup, but reverse roles
        MumbleOCB2 client = new MumbleOCB2();
        assertTrue(client.setKey(key, clientIV, serverIV), "client:setKey failed");
        MumbleOCB2 server = new MumbleOCB2();
        assertTrue(server.setKey(key, serverIV, clientIV), "server:setKey failed");

        byte[] ct = server.encrypt(TEST_MESSAGE.getBytes());
        assertNotNull(ct, "encryption returned null");
        assertEquals(EXPECTED_C2S, bytesToHex(ct), "server→client ciphertext");

        byte[] pt = client.decrypt(ct);
        assertNotNull(pt, "decryption returned null on client");
        assertEquals(TEST_MESSAGE, new String(pt), "client decrypted plaintext");
    }

    //----------------------------------------------------------------------

    // utility for the rest of the tests
    private static final byte[] TEST_KEY    = new byte[16];
    private static final byte[] SERVER_IV_A  = new byte[16];
    private static final byte[] SERVER_IV_B  = new byte[16];

    @BeforeEach
    public void initConstants() {
        for (int i = 0; i < 16; i++) {
            TEST_KEY[i]    = (byte)(i * 3 + 7);
            SERVER_IV_A[i] = (byte)(0x30 + i);
            SERVER_IV_B[i] = (byte)(0x40 + i);
        }
    }

    /**
     * Build a server/client pair that mirror each other:
     *   - server.encrypt ↔ client.decrypt
     */
    private MumbleOCB2[] makePair() {
        // server sees its own “clientNonce” = SERVER_IV_B, "serverNonce" = SERVER_IV_A
        MumbleOCB2 server = new MumbleOCB2();
        assertTrue(server.setKey(TEST_KEY, SERVER_IV_B, SERVER_IV_A), "server:setKey must succeed");
        // client sees its own “clientNonce” = SERVER_IV_A, "serverNonce" = SERVER_IV_B
        MumbleOCB2 client = new MumbleOCB2();
        assertTrue(client.setKey(TEST_KEY, SERVER_IV_A, SERVER_IV_B), "client:setKey must succeed");
        return new MumbleOCB2[]{ server, client };
    }

    @Test
    public void testEmptyPlaintext() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        byte[] empty = new byte[0];
        byte[] ct = pair[0].encrypt(empty);
        assertNotNull(ct, "encrypt(empty) must not be null");
        assertEquals(4, ct.length, "empty‐plaintext ciphertext length");

        byte[] pt = pair[1].decrypt(ct);
        assertNotNull(pt, "decrypt(empty) must not be null");
        assertEquals(0, pt.length, "empty‐plaintext roundtrip");
    }

    @Test
    public void testSingleBytePlaintext() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        byte[] one = new byte[]{ (byte)0xAB };
        byte[] ct = pair[0].encrypt(one);
        assertNotNull(ct);
        assertTrue(ct.length > 4, "should carry at least one ciphertext byte");

        byte[] pt = pair[1].decrypt(ct);
        assertNotNull(pt);
        assertArrayEquals(one, pt, "round‐trip single byte");
    }

    @Test
    public void testPartialBlocksVariousLengths() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        for (int len : new int[]{ 1, 15, 16, 17, 31, 32, 33 }) {
            byte[] pt = new byte[len];
            for (int i = 0; i < len; i++) pt[i] = (byte)(i*7 + len);
            byte[] ct = pair[0].encrypt(pt);
            assertNotNull(ct,   "encrypt(len="+len+")");
            byte[] dt = pair[1].decrypt(ct);
            assertNotNull(dt,   "decrypt(len="+len+")");
            assertArrayEquals(pt, dt, "roundtrip len="+len);
        }
    }

    @Test
    public void testSequentialMessagesAdvanceIV() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        byte[] msg1 = "First message".getBytes();
        byte[] msg2 = "Second message".getBytes();

        byte[] ct1 = pair[0].encrypt(msg1);
        byte[] pt1 = pair[1].decrypt(ct1);
        assertArrayEquals(msg1, pt1, "first roundtrip");

        byte[] ct2 = pair[0].encrypt(msg2);
        assertNotEquals(ct1[0], ct2[0], "IV must have incremented");
        byte[] pt2 = pair[1].decrypt(ct2);
        assertArrayEquals(msg2, pt2, "second roundtrip");
    }

    @Test
    public void testTagTamperingCausesFailure() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        byte[] msg = "Integrity!".getBytes();
        byte[] ct  = pair[0].encrypt(msg);
        ct[1] ^= 1; // flip a bit in the tag
        assertThrows(MumbleOCB2.DecryptException.class, () -> pair[1].decrypt(ct), "decrypt must return null on bad tag");
    }

    @Test
    public void testCiphertextTamperingCausesFailure() throws MumbleOCB2.DecryptException {
        var pair = makePair();
        byte[] msg = "Test 123".getBytes();
        byte[] ct  = pair[0].encrypt(msg);
        if (ct.length > 4) {
            ct[4] ^= (byte) 0x80; // flip a bit in the data
            assertThrows(MumbleOCB2.DecryptException.class, () -> pair[1].decrypt(ct), "decrypt must throw on bad ciphertext");
        }
    }

    //───────────────────────────────────────────────────────────────────────────

    // hex ↔ byte[] helpers

    private static byte[] hexToBytes(String hex) {
        int len = hex.length(), idx = 0;
        byte[] out = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            out[idx++] = (byte) Integer.parseInt(hex.substring(i, i+2), 16);
        }
        return out;
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length*2);
        for (byte b : data) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}
