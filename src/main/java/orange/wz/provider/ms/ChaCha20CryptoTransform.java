package orange.wz.provider.ms;

import java.util.Arrays;

/**
 * ChaCha20 stream cipher (RFC 7539), ported from WzComparerR2.
 */
public final class ChaCha20CryptoTransform implements AutoCloseable {
    public static final int ALLOWED_KEY_LENGTH = 32;
    public static final int ALLOWED_NONCE_LENGTH = 12;
    public static final int PROCESS_BYTES_AT_TIME = 64;
    public static final int STATE_LENGTH = 16;

    private final int[] state = new int[STATE_LENGTH];
    private boolean disposed;

    public ChaCha20CryptoTransform(byte[] key, byte[] nonce, int counter) {
        if (key == null || key.length != ALLOWED_KEY_LENGTH) {
            throw new IllegalArgumentException("Key length must be " + ALLOWED_KEY_LENGTH);
        }
        if (nonce == null || nonce.length != ALLOWED_NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce length must be " + ALLOWED_NONCE_LENGTH);
        }
        keySetup(key);
        ivSetup(nonce, counter);
    }

    public int[] getState() {
        return state;
    }

    public void transformBlock(byte[] input, int inputOffset, byte[] output, int outputOffset) {
        if (disposed) {
            throw new IllegalStateException("ChaCha20 transform disposed");
        }
        int len = input.length - inputOffset;
        if (len <= 0 || (len & 63) != 0) {
            throw new IllegalArgumentException("Input length must be a positive multiple of 64");
        }
        int written = 0;
        while (written < len) {
            transformBlock64(input, inputOffset + written, output, outputOffset + written);
            written += PROCESS_BYTES_AT_TIME;
        }
    }

    public byte[] transform(byte[] input) {
        if (input.length == 0) {
            return new byte[0];
        }
        int padded = (input.length + 63) & ~63;
        byte[] paddedInput = Arrays.copyOf(input, padded);
        byte[] output = new byte[padded];
        transformBlock(paddedInput, 0, output, 0);
        if (padded == input.length) {
            return output;
        }
        return Arrays.copyOf(output, input.length);
    }

    private void transformBlock64(byte[] input, int inOff, byte[] output, int outOff) {
        int[] x = new int[STATE_LENGTH];
        byte[] tmp = new byte[PROCESS_BYTES_AT_TIME];
        System.arraycopy(state, 0, x, 0, STATE_LENGTH);

        for (int round = 0; round < 10; round++) {
            quarterRound(x, 0, 4, 8, 12);
            quarterRound(x, 1, 5, 9, 13);
            quarterRound(x, 2, 6, 10, 14);
            quarterRound(x, 3, 7, 11, 15);
            quarterRound(x, 0, 5, 10, 15);
            quarterRound(x, 1, 6, 11, 12);
            quarterRound(x, 2, 7, 8, 13);
            quarterRound(x, 3, 4, 9, 14);
        }

        for (int i = 0; i < STATE_LENGTH; i++) {
            toBytes(tmp, add(x[i], state[i]), i * 4);
        }

        state[12] = addOne(state[12]);
        if (state[12] == 0) {
            state[13] = addOne(state[13]);
        }

        for (int i = 0; i < PROCESS_BYTES_AT_TIME; i++) {
            output[outOff + i] = (byte) ((input[inOff + i] & 0xFF) ^ (tmp[i] & 0xFF));
        }
    }

    private void keySetup(byte[] key) {
        byte[] constants = "expand 32-byte k".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int keyIndex = key.length - 16;
        state[4] = u8To32Little(key, 0);
        state[5] = u8To32Little(key, 4);
        state[6] = u8To32Little(key, 8);
        state[7] = u8To32Little(key, 12);
        state[8] = u8To32Little(key, keyIndex);
        state[9] = u8To32Little(key, keyIndex + 4);
        state[10] = u8To32Little(key, keyIndex + 8);
        state[11] = u8To32Little(key, keyIndex + 12);
        state[0] = u8To32Little(constants, 0);
        state[1] = u8To32Little(constants, 4);
        state[2] = u8To32Little(constants, 8);
        state[3] = u8To32Little(constants, 12);
    }

    private void ivSetup(byte[] nonce, int counter) {
        state[12] = counter;
        state[13] = u8To32Little(nonce, 0);
        state[14] = u8To32Little(nonce, 4);
        state[15] = u8To32Little(nonce, 8);
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] = add(x[a], x[b]);
        x[d] = rotate(xor(x[d], x[a]), 16);
        x[c] = add(x[c], x[d]);
        x[b] = rotate(xor(x[b], x[c]), 12);
        x[a] = add(x[a], x[b]);
        x[d] = rotate(xor(x[d], x[a]), 8);
        x[c] = add(x[c], x[d]);
        x[b] = rotate(xor(x[b], x[c]), 7);
    }

    private static int rotate(int v, int c) {
        return (v << c) | (v >>> (32 - c));
    }

    private static int xor(int v, int w) {
        return v ^ w;
    }

    private static int add(int v, int w) {
        return v + w;
    }

    private static int addOne(int v) {
        return v + 1;
    }

    private static int u8To32Little(byte[] p, int offset) {
        return (p[offset] & 0xFF)
                | ((p[offset + 1] & 0xFF) << 8)
                | ((p[offset + 2] & 0xFF) << 16)
                | ((p[offset + 3] & 0xFF) << 24);
    }

    private static void toBytes(byte[] output, int input, int outputOffset) {
        output[outputOffset] = (byte) input;
        output[outputOffset + 1] = (byte) (input >>> 8);
        output[outputOffset + 2] = (byte) (input >>> 16);
        output[outputOffset + 3] = (byte) (input >>> 24);
    }

    @Override
    public void close() {
        if (!disposed) {
            Arrays.fill(state, 0);
            disposed = true;
        }
    }
}
