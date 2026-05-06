package orange.wz.gui.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 将 VP8/VP9 帧封装为 IVF，供 FFmpeg 解码。
 */
public final class IvfMuxer {

    private IvfMuxer() {
    }

    public static byte[] buildIvf(int fourCc, int width, int height, List<byte[]> framePayloads) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFileHeader(out, fourCc, width, height, framePayloads.size());
        long ts = 0;
        for (byte[] payload : framePayloads) {
            writeFrame(out, payload, ts);
            ts++;
        }
        return out.toByteArray();
    }

    private static void writeFileHeader(ByteArrayOutputStream out, int fourCc, int width, int height, int numFrames)
            throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        hdr.put("DKIF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        hdr.putShort((short) 0);
        hdr.putShort((short) 32);
        hdr.putInt(fourCc);
        hdr.putShort((short) width);
        hdr.putShort((short) height);
        hdr.putInt(30);
        hdr.putInt(1);
        hdr.putInt(numFrames);
        hdr.putInt(0);
        out.write(hdr.array());
    }

    private static void writeFrame(ByteArrayOutputStream out, byte[] payload, long timestamp) throws IOException {
        ByteBuffer fh = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        fh.putInt(payload.length);
        fh.putLong(timestamp);
        out.write(fh.array());
        out.write(payload);
    }
}
