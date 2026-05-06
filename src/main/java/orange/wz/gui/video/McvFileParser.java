package orange.wz.gui.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 解析 Maple Canvas#Video 内嵌的 MCV0 头与帧表（对齐 WzComparerR2 {@code Wz_Video.ReadVideoFileHeader}）。
 */
public final class McvFileParser {

    private static final int XOR_FOURCC = 0xa5a5a5a5;

    private McvFileParser() {
    }

    public static McvHeader parse(byte[] mcvPayload) {
        if (mcvPayload == null || mcvPayload.length < 40) {
            throw new IllegalArgumentException("MCV 数据过短");
        }
        ByteBuffer buf = ByteBuffer.wrap(mcvPayload).order(ByteOrder.LITTLE_ENDIAN);
        byte[] sig = new byte[4];
        buf.get(sig);
        String signature = new String(sig, StandardCharsets.US_ASCII);
        if (!"MCV0".equals(signature)) {
            throw new IllegalArgumentException("不是有效的 MCV0 文件头: " + signature);
        }
        buf.position(buf.position() + 2);
        int headerLen = Short.toUnsignedInt(buf.getShort());
        int fourCc = buf.getInt() ^ XOR_FOURCC;
        int width = Short.toUnsignedInt(buf.getShort());
        int height = Short.toUnsignedInt(buf.getShort());
        int frameCount = buf.getInt();
        int dataFlag = Byte.toUnsignedInt(buf.get());
        buf.position(buf.position() + 3);
        long frameDelayUnit = buf.getLong();
        int defaultDelay = buf.getInt();
        if (headerLen < 0 || headerLen > mcvPayload.length) {
            throw new IllegalArgumentException("非法 headerLength: " + headerLen);
        }
        buf.position(headerLen);

        McvFrameInfo[] frames = new McvFrameInfo[frameCount];
        for (int i = 0; i < frameCount; i++) {
            McvFrameInfo fi = new McvFrameInfo();
            fi.setDataOffset(buf.getInt());
            fi.setDataCount(buf.getInt());
            frames[i] = fi;
        }
        if ((dataFlag & McvDataFlags.ALPHA_MAP) != 0) {
            for (int i = 0; i < frameCount; i++) {
                frames[i].setAlphaDataOffset(buf.getInt());
                frames[i].setAlphaDataCount(buf.getInt());
            }
        }
        if ((dataFlag & McvDataFlags.PER_FRAME_DELAY) != 0) {
            for (int i = 0; i < frameCount; i++) {
                long d = (buf.getInt() & 0xffffffffL) * frameDelayUnit;
                frames[i].setDelayNanoseconds(d);
            }
        } else {
            long d = (long) defaultDelay * frameDelayUnit;
            for (int i = 0; i < frameCount; i++) {
                frames[i].setDelayNanoseconds(d);
            }
        }
        if ((dataFlag & McvDataFlags.PER_FRAME_TIMELINE) != 0) {
            for (int i = 0; i < frameCount; i++) {
                long t = buf.getLong() * frameDelayUnit;
                frames[i].setStartTimeNanoseconds(t);
            }
        } else {
            long time = 0;
            for (int i = 0; i < frameCount; i++) {
                frames[i].setStartTimeNanoseconds(time);
                time += frames[i].getDelayNanoseconds();
            }
        }

        long dataStartPosition = buf.position();
        for (McvFrameInfo fi : frames) {
            fi.setDataOffset(fi.getDataOffset() + dataStartPosition);
            if (fi.getAlphaDataCount() > 0 && fi.getAlphaDataOffset() >= 0) {
                fi.setAlphaDataOffset(fi.getAlphaDataOffset() + dataStartPosition);
            }
        }

        McvHeader h = new McvHeader();
        h.setHeaderLength(headerLen);
        h.setFourCc(fourCc);
        h.setWidth(width);
        h.setHeight(height);
        h.setFrameCount(frameCount);
        h.setDataFlags(dataFlag);
        h.setFrameDelayUnit(frameDelayUnit);
        h.setDefaultDelay(defaultDelay);
        h.setFrames(frames);
        return h;
    }
}
