package orange.wz.provider.ms;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/**
 * Buffered ChaCha20 reader over a byte array region.
 */
final class ChaCha20Reader implements AutoCloseable {
    private final byte[] data;
    private int dataPos;
    private final ChaCha20CryptoTransform cipher;
    private final byte[] buffer = new byte[ChaCha20CryptoTransform.PROCESS_BYTES_AT_TIME];
    private int bufferReadPos = buffer.length;

    ChaCha20Reader(byte[] data, int startPos, byte[] key, byte[] nonce) {
        this.data = data;
        this.dataPos = startPos;
        this.cipher = new ChaCha20CryptoTransform(key, nonce, 0);
    }

    /** 已消耗的密文字节偏移（与 WzComparerR2 BaseStream.Position 一致） */
    int physicalPosition() {
        return dataPos;
    }

    int readInt32() {
        byte[] temp = readBytes(4);
        return ByteBuffer.wrap(temp).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    String readString() {
        int strLen = readInt32();
        byte[] raw = readBytes(strLen * 2);
        char[] chars = new char[strLen];
        for (int i = 0; i < strLen; i++) {
            chars[i] = (char) ((raw[i * 2] & 0xFF) | ((raw[i * 2 + 1] & 0xFF) << 8));
        }
        return new String(chars);
    }

    byte[] readBytes(int count) {
        byte[] out = new byte[count];
        readBytes(out, 0, count);
        return out;
    }

    void readBytes(byte[] dest, int destOff, int count) {
        int written = 0;
        while (written < count) {
            if (bufferReadPos >= buffer.length) {
                if (dataPos + buffer.length > data.length) {
                    throw new IllegalStateException("ChaCha20Reader: unexpected end of data");
                }
                System.arraycopy(data, dataPos, buffer, 0, buffer.length);
                cipher.transformBlock(buffer, 0, buffer, 0);
                dataPos += buffer.length;
                bufferReadPos = 0;
            }
            int copy = Math.min(count - written, buffer.length - bufferReadPos);
            System.arraycopy(buffer, bufferReadPos, dest, destOff + written, copy);
            bufferReadPos += copy;
            written += copy;
        }
        if (bufferReadPos >= buffer.length) {
            cipher.getState()[12] = 0;
            bufferReadPos = buffer.length;
        }
    }

    @Override
    public void close() {
        cipher.close();
    }
}
