package orange.wz.provider.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.tools.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@Getter
@Slf4j
public class WzPngProperty extends WzImageProperty {

    /**
     * 强力压缩等流程：先按「未勾选旧版技能特效」写入节点，成功后再 {@link #applyLegacySkillEffectAfterStrongCompress}。
     */
    private static final ThreadLocal<Boolean> SKIP_LEGACY_SKILL_EFFECT_FOR_THIS_COMPRESS = new ThreadLocal<>();

    public static void setSkipLegacySkillEffectForThisCompress(boolean skip) {
        if (skip) {
            SKIP_LEGACY_SKILL_EFFECT_FOR_THIS_COMPRESS.set(Boolean.TRUE);
        } else {
            SKIP_LEGACY_SKILL_EFFECT_FOR_THIS_COMPRESS.remove();
        }
    }

    private static boolean isSkipLegacySkillEffectForThisCompress() {
        return Boolean.TRUE.equals(SKIP_LEGACY_SKILL_EFFECT_FOR_THIS_COMPRESS.get());
    }
    public static class CompressedPngData {
        private final int width;
        private final int height;
        private final WzPngFormat format;
        private final int scale;
        private final boolean listWzUsed;
        private final byte[] compressedBytes;

        public CompressedPngData(int width, int height, WzPngFormat format, int scale, boolean listWzUsed, byte[] compressedBytes) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.scale = scale;
            this.listWzUsed = listWzUsed;
            this.compressedBytes = compressedBytes;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public WzPngFormat getFormat() {
            return format;
        }

        public int getScale() {
            return scale;
        }

        public boolean isListWzUsed() {
            return listWzUsed;
        }

        public byte[] getCompressedBytes() {
            return compressedBytes;
        }
    }

    private int width;
    private int height;
    private WzPngFormat format;
    private int scale;
    private int offset;
    @Getter(AccessLevel.NONE)
    private byte[] compressedBytes;
    private boolean listWzUsed;
    @Getter(AccessLevel.NONE)
    private BufferedImage image;

    public WzPngProperty(String name, WzObject parent, WzImage wzImage) {
        super(name, WzType.PNG_PROPERTY, parent, wzImage);
    }

    public WzPngProperty(String name, int format, int scale, byte[] imageBytes, WzObject parent, WzImage wzImage) {
        this(name, parent, wzImage);
        this.format = WzPngFormat.getByValue(format);
        this.scale = scale;

        if (imageBytes != null && imageBytes.length > 0) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                BufferedImage image = ImageIO.read(bis);
                if (image == null) {
                    throw new IOException("无法解码图片数据，可能不是支持的图片格式");
                }
                this.image = image;
                this.width = image.getWidth();
                this.height = image.getHeight();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
	
    public WzPngProperty(String name, int width, int height, int format, int scale, byte[] imageBytes, WzObject parent, WzImage wzImage) {
        this(name, parent, wzImage);
        this.width = width;
        this.height = height;
        this.format = WzPngFormat.getByValue(format);
        this.scale = scale;

        if (imageBytes != null && imageBytes.length > 0) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                BufferedImage image = ImageIO.read(bis);
                if (image == null) {
                    throw new IOException("无法解码图片数据，可能不是支持的图片格式");
                }
                this.image = image;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Setter And Parse ------------------------------------------------------------------------------------------------
    public void setData(BinaryReader reader) {
        width = reader.readCompressedInt();
        height = reader.readCompressedInt();
        // 兼容两种 PNG 头部布局：
        // - Harepacker/经典：format1(int) + format2(int) -> format = format1 + (format2 << 8)，之后再跳过4字节
        // - 本项目旧实现：format(int) + scale(byte) + 4字节
        int format1 = reader.readCompressedInt();
        int posAfterFormat1 = reader.getPosition();
        int format2 = reader.readCompressedInt();
        int combined = format1 + (format2 << 8);
        if (isKnownFormat(combined)) {
            format = WzPngFormat.getByValue(combined);
            // Harepacker 这条布局里没有 scale 字段；我们用 0 表示 scale=1
            scale = 0;
        } else {
            // 回退到旧布局：恢复位置，把 format1 当完整 format，并读取 scale(byte)
            reader.setPosition(posAfterFormat1);
            format = WzPngFormat.getByValue(format1);
            scale = reader.getByte();
        }

        // 将 Harepacker 的 Format3/Format517 映射为现有解码路径（最小可读，避免崩溃）
        if (format == WzPngFormat.FORMAT3) {
            // 以 ARGB4444 + scale=2 的路径解码（1<<1=2）
            format = WzPngFormat.ARGB4444;
            scale = 1;
        } else if (format == WzPngFormat.FORMAT517) {
            // 以 RGB565 + scale=4 的路径解码（1<<2=4）
            format = WzPngFormat.RGB565;
            scale = 2;
        }

        reader.skip(4); // 跳过4个字节
        offset = reader.getPosition();
    }

    private boolean isKnownFormat(int value) {
        for (WzPngFormat e : WzPngFormat.values()) {
            if (e.getValue() == value) {
                return true;
            }
        }
        return false;
    }

    public void setImage(BufferedImage image, WzPngFormat format, int scale) {
        setImage(image, format, scale, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * @param zlibCompressionLevel {@link Deflater} 级别，常用 {@link Deflater#BEST_COMPRESSION} (9)
     */
    public void setImage(BufferedImage image, WzPngFormat format, int scale, int zlibCompressionLevel) {
        setImage(image, format, scale, zlibCompressionLevel, WzPngZlibCompressMode.DEFAULT);
    }

    /**
     * @param zlibMode zlib 策略；{@link WzPngZlibCompressMode#BRUTE_SMALLEST} 会多次压缩取最小体积累积 zlib 块
     */
    public void setImage(BufferedImage image, WzPngFormat format, int scale, int zlibCompressionLevel,
                         WzPngZlibCompressMode zlibMode) {
        this.format = format;
        this.scale = scale;
        this.image = image;
        compressImage(zlibCompressionLevel, zlibMode);
    }

    public CompressedPngData exportCompressedData() {
        byte[] bytes = getCompressedBytes(false);
        if (bytes == null) {
            return null;
        }
        return new CompressedPngData(
                width,
                height,
                format,
                scale,
                listWzUsed,
                Arrays.copyOf(bytes, bytes.length)
        );
    }

    public void copyCompressedFrom(CompressedPngData data, boolean keepImageInMem) {
        if (data == null || data.getCompressedBytes() == null) {
            throw new IllegalArgumentException("压缩图片数据为空");
        }
        width = data.getWidth();
        height = data.getHeight();
        format = data.getFormat();
        scale = data.getScale();
        listWzUsed = data.isListWzUsed();
        compressedBytes = Arrays.copyOf(data.getCompressedBytes(), data.getCompressedBytes().length);
        offset = 0;
        image = null;
        if (keepImageInMem) {
            parse(true);
        }
    }

    private void parse(boolean saveInMem) {
        byte[] compressedBytes = getCompressedBytes(saveInMem);
        if (compressedBytes == null) {
            log.warn("{} 没有图像数据", getPath());
            return;
        }
        byte[] rawBytes = decompress(compressedBytes);
        if (rawBytes.length == 0) {
            throw new RuntimeException("rawBytes 是空的");
        }
        BinaryReader rawBytesReader = new BinaryReader(rawBytes); // rawBytes 是小端序的，用Reader读更方便

        int[] argb32 = new int[width * height];
        int imageType = ImgTool.getBufferImageType(format);
        BufferedImage img = new BufferedImage(width, height, imageType);
        int actualScale = getActualScale();

        switch (format) {
            case WzPngFormat.ARGB4444:
                if (getActualScale() == 1) {
                    for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                        argb32[i] = ImgTool.Argb32.fromArgb4444(rawBytesReader.getShort());
                    }
                    img.setRGB(0, 0, width, height, argb32, 0, width);
                } else {
                    // 原来的 Format3 (format 1 + scale 2) 实际上几乎没见过这个 // https://forum.ragezone.com/threads/new-wz-png-format-decode-code.1114978/
                    if (width % actualScale != 0 || height % actualScale != 0) {
                        throw new IllegalArgumentException("width 和 height 不能被 scale 整除");
                    }
                    int rawWidth = width / actualScale;
                    int rawHeight = height / actualScale;
                    int[] rawArgb32 = new int[rawWidth * rawHeight];
                    for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                        rawArgb32[i] = ImgTool.Argb32.fromArgb4444(rawBytesReader.getShort());
                    }
                    argb32 = ImgTool.Argb32.upscale(rawArgb32, rawWidth, rawHeight, actualScale);
                    img.setRGB(0, 0, width, height, argb32, 0, width);
                }
                break;
            case WzPngFormat.ARGB8888:
                // UI.wz/UIWindow2.img/MonsterKilling/Count/keyBackgrd/ing
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB8888 + " 不支持 scale");
                }
                for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                    argb32[i] = rawBytesReader.getInt();
                }
                img.setRGB(0, 0, width, height, argb32, 0, width);
                break;
            case WzPngFormat.ARGB1555:
                // "Npc.wz\\2570101.img\\info\\illustration2\\face\\0" // 2570107 is a decent example. Used KMS 353
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB1555 + " 不支持 scale");
                }
                for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                    argb32[i] = ImgTool.Argb32.fromArgb1555(rawBytesReader.getShort());
                }
                img.setRGB(0, 0, width, height, argb32, 0, width);
                break;
            case WzPngFormat.RGB565:
                if (getActualScale() == 1) {
                    // UI.wz/Logo.img v95
                    for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                        argb32[i] = ImgTool.Argb32.fromRgb565(rawBytesReader.getShort());
                    }
                    img.setRGB(0, 0, width, height, argb32, 0, width);
                } else {
                    // 原来的 Format 517 (format 513 + scale 4) // FullPath = "Map.wz\\Back\\midForest.img\\back\\0"
                    if (width % actualScale != 0 || height % actualScale != 0) {
                        throw new IllegalArgumentException("width 和 height 不能被 scale 整除");
                    }
                    int rawWidth = width / actualScale;
                    int rawHeight = height / actualScale;
                    int[] rawArgb32 = new int[rawWidth * rawHeight];
                    for (int i = 0; rawBytesReader.hasRemaining(); i++) {
                        rawArgb32[i] = ImgTool.Argb32.fromRgb565(rawBytesReader.getShort());
                    }
                    argb32 = ImgTool.Argb32.upscale(rawArgb32, rawWidth, rawHeight, actualScale);
                    img.setRGB(0, 0, width, height, argb32, 0, width);
                }
                break;
            case WzPngFormat.DXT3:
                // Familiar_000.wz\9960688.img\attack\info\hit\0
                // Effect_004.wz\Direction17.img\effect\ark\noise\800\0\24
                // Effect_017.wz\EliteMobEff.img\eliteMonster\0\0
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.DXT3 + " 不支持 scale");
                }
                argb32 = ImgTool.Argb32.fromDXT3(rawBytesReader, width, height);
                img.setRGB(0, 0, width, height, argb32, 0, width);
                break;
            case WzPngFormat.DXT5:
                // Skill_022.wz/40002.img/skill/400021006/effect
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.DXT5 + " 不支持 scale");
                }
                argb32 = ImgTool.Argb32.fromDXT5(rawBytesReader, width, height);
                img.setRGB(0, 0, width, height, argb32, 0, width);
                break;
            case WzPngFormat.BC7:
                // CMS220 Character/TamingMob/_Canvas/_Canvas_007.wz/01984266.img/sit/0/tamingMobRear
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.BC7 + " 不支持 scale");
                }
                argb32 = ImgTool.Argb32.fromBC7(rawBytesReader, width & ~3, height & ~3);
                img.setRGB(0, 0, width, height, argb32, 0, width);
                break;
        }

        image = img;
    }

    public void clearImage() {
        if (image != null) {
            image.flush();
        }
        image = null;
    }

    /**
     * 丢弃堆上的压缩数据副本（仍可从 {@link #offset} + reader 再读），减轻预览后内存占用。
     */
    public void discardReloadableCompressedCopy() {
        if (offset != 0 && wzImage != null && wzImage.getReader() != null) {
            compressedBytes = null;
        }
    }

    // Getter ----------------------------------------------------------------------------------------------------------
    public BufferedImage getImage(boolean saveInMem) {
        if (image == null) {
            parse(saveInMem);
        }

        return image;
    }

    public byte[] getImageBytes(boolean saveInMem) {
        BufferedImage image = getImage(saveInMem);
        if (image == null) return null;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", stream);
            return stream.toByteArray();
        } catch (IOException e) {
            log.error("加载图片二进制数据失败 节点: {} 消息: {}", getPath(), e.getMessage());
        }
        return null;
    }

    private int getActualScale() {
        return scale > 0 ? (1 << scale) : 1;
    }

    /**
     * 与 {@link #peekListWzUsedFromBlob} / {@link #decodeToZlibPayload} 一致，用于压缩流程里刷新标志。
     */
    private void applyListWzUsedFromPngHeader(byte[] data) {
        listWzUsed = peekListWzUsedFromBlob(data);
    }

    /** Nexon 单块异或：小端 int 后紧跟 n 字节密文，且 n == 总长 - 4（与 compressBytes 中 putInt+zxor 一致） */
    private static boolean isSingleBlockXorLengthPrefix(byte[] data) {
        if (data == null || data.length < 6) {
            return false;
        }
        int declared = readIntLE(data, 0);
        return declared > 0 && declared == data.length - 4;
    }

    private static int readIntLE(byte[] data, int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }

    /** 历史上用来判定裸 zlib 的常见 ushort（小端前两字节） */
    private static boolean legacyPlainZlibMagicUshort(int header16) {
        return header16 == 0x9C78 || header16 == 0xDA78 || header16 == 0x0178;
    }

    /** RFC1950 zlib wrapper：CMF/FLG 可被 31 整除且 CMF 低 4 位为 8（deflate） */
    private static boolean looksLikeRawZlibRfc1950(byte[] data, int offset) {
        if (data == null || data.length - offset < 2) {
            return false;
        }
        int cmf = data[offset] & 0xFF;
        int flg = data[offset + 1] & 0xFF;
        int hdr = (cmf << 8) | flg;
        if (hdr % 31 != 0) {
            return false;
        }
        return (cmf & 0x0F) == 8;
    }

    /**
     * 是否需要密钥参与解压（与 {@link #decodeToZlibPayload} 判定顺序一致）。
     */
    private static boolean peekListWzUsedFromBlob(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        if (isSingleBlockXorLengthPrefix(data)) {
            return true;
        }
        int header = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
        if (legacyPlainZlibMagicUshort(header) || looksLikeRawZlibRfc1950(data, 0)) {
            return false;
        }
        return true;
    }

    /**
     * 将 IMG 内嵌的 PNG 压缩块还原为可直接 zlib inflate 的字节。
     * <p>
     * 必须先识别 Nexon「单块长度前缀 + 异或」格式：若 zlib 压缩后长度为 0x9C78，则包前缀为小端
     * {@code 78 9C 00 00}，前两字节与传统 zlib 魔数相同，仅用 ushort 判断会误判为裸 zlib，
     * 把整个 blob（含 4 字节长度）交给 Inflater → {@code ZipException: incorrect header check}。
     */
    /**
     * 分块异或解密用的密钥：与 {@link #compressBytes} / {@link #wrapZlibPayloadWithLegacySkillChunks} 写入侧一致。
     * 勾选「旧版技能特效」时用固定 WZ_MSEAIV + USER_KEY；否则用当前 Reader 的 List.wz 类密钥。
     */
    private static WzMutableKey xorKeyForChunkedListStyleDecode(WzMutableKey readerKey) {
        try {
            if (orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption()) {
                return new WzMutableKey(
                        orange.wz.provider.tools.CryptoConstants.WZ_MSEAIV,
                        orange.wz.provider.tools.CryptoConstants.USER_KEY);
            }
        } catch (Exception ignored) {
        }
        return readerKey;
    }

    /**
     * 从 IMG 内嵌块还原 zlib payload（与 {@link #decodeToZlibPayload} 算法一致，但不改动 {@link #listWzUsed}）。
     */
    private byte[] peelToZlibPayload(byte[] compressedBytes, WzMutableKey wzMutableKey) {
        if (isSingleBlockXorLengthPrefix(compressedBytes)) {
            if (wzMutableKey == null) {
                throw new RuntimeException("单块 List.wz 异或数据缺少 WzMutableKey");
            }
            int n = readIntLE(compressedBytes, 0);
            byte[] zlibBody = new byte[n];
            for (int i = 0; i < n; i++) {
                zlibBody[i] = (byte) (compressedBytes[4 + i] ^ wzMutableKey.get(i));
            }
            return zlibBody;
        }

        int header = ((compressedBytes[1] & 0xFF) << 8) | (compressedBytes[0] & 0xFF);
        if (legacyPlainZlibMagicUshort(header) || looksLikeRawZlibRfc1950(compressedBytes, 0)) {
            return compressedBytes;
        }

        WzMutableKey xorKey = xorKeyForChunkedListStyleDecode(wzMutableKey);
        if (xorKey == null) {
            throw new RuntimeException("分块异或数据缺少 WzMutableKey");
        }
        BinaryReader reader = new BinaryReader(compressedBytes);
        BinaryWriter writer = new BinaryWriter();
        while (reader.hasRemaining()) {
            int posBefore = reader.getPosition();
            int blockSize = reader.getInt();
            int payloadAvailable = compressedBytes.length - reader.getPosition();
            if (blockSize < 0 || blockSize > payloadAvailable) {
                throw new RuntimeException(String.format(
                        "无效的 List.wz 分块：declared=%d, 剩余=%d (offset=%d, len=%d)",
                        blockSize, payloadAvailable, posBefore, compressedBytes.length));
            }
            for (int i = 0; i < blockSize; i++) {
                writer.putByte((byte) (reader.getByte() ^ xorKey.get(i)));
            }
        }
        return writer.output();
    }

    private byte[] decodeToZlibPayload(byte[] compressedBytes, WzMutableKey wzMutableKey) {
        if (isSingleBlockXorLengthPrefix(compressedBytes)) {
            listWzUsed = true;
            return peelToZlibPayload(compressedBytes, wzMutableKey);
        }

        int header = ((compressedBytes[1] & 0xFF) << 8) | (compressedBytes[0] & 0xFF);
        if (legacyPlainZlibMagicUshort(header) || looksLikeRawZlibRfc1950(compressedBytes, 0)) {
            listWzUsed = false;
            return compressedBytes;
        }

        listWzUsed = true;
        return peelToZlibPayload(compressedBytes, wzMutableKey);
    }

    // Decompress ------------------------------------------------------------------------------------------------------
    private InflaterInputStream createZlibStream(byte[] compressedBytes, WzMutableKey wzMutableKey) {
        byte[] zlibPayload = decodeToZlibPayload(compressedBytes, wzMutableKey);
        return new InflaterInputStream(new ByteArrayInputStream(zlibPayload));
    }

    private byte[] decompress(byte[] compressedBytes) {
        applyListWzUsedFromPngHeader(compressedBytes);
        int size = ImgTool.getRawByteSize(format, getActualScale(), width, height);
        byte[] rawBytes = new byte[size]; // decompress byte
        WzMutableKey wzMutableKey = null;
        if (listWzUsed) {
            if (wzImage == null || wzImage.getReader() == null) {
                throw new RuntimeException("listWz 图片解密失败：缺少 WzReader 上下文");
            }
            wzMutableKey = wzImage.getReader().getWzMutableKey();
            if (wzMutableKey == null) {
                throw new RuntimeException(
                        "listWz 图片解密失败：WzMutableKey 未初始化（无法异或解密）。请确认 WZ 已从正确密钥的 Reader 打开。");
            }
        }
        // 使用 try-with-resources 确保资源正确关闭
        try (InflaterInputStream zlib = createZlibStream(compressedBytes, wzMutableKey)) {
            // zlib.read(decBuf, 0, uncompressedSize); 可能一次读不完全部数据，要循环确认，所以有了这个方法
            int totalRead = 0;
            int bytesRead;
            while (totalRead < size && (bytesRead = zlib.read(rawBytes, totalRead, size - totalRead)) != -1) {
                totalRead += bytesRead;
            }

            return rawBytes;
        } catch (Exception e) {
            log.error(getPath());
            throw new RuntimeException(e);
        }
    }

    // Compress --------------------------------------------------------------------------------------------------------
    private byte[] getRawBytes(BufferedImage img, WzPngFormat format) {
        int[] argb32 = ImgTool.Argb32.fromBufferedImage(img);
        BinaryWriter writer = new BinaryWriter(false); // 把数据转为小端序
        int actualScale = getActualScale();
        return switch (format) {
            case WzPngFormat.ARGB4444 -> {
                if (actualScale > 1) {
                    argb32 = ImgTool.Argb32.downscale(argb32, width, height, actualScale, true);
                }

                for (int v : argb32) {
                    writer.putShort(ImgTool.Argb32.toArgb4444(v));
                }
                yield writer.output();
            }
            case WzPngFormat.ARGB8888 -> {
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB8888 + " 不支持 scale");
                }
                boolean useOldEnc = false;
                if (!isSkipLegacySkillEffectForThisCompress()) {
                    try {
                        useOldEnc = orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption();
                    } catch (Exception e) {
                        // ignore if MainFrame is not available
                    }
                }
                
                if (useOldEnc) {
                    // 旧版技能特效模式：使用 BGRA 顺序，和 MapleLib 保持一致
                    for (int v : argb32) {
                        int a = (v >>> 24) & 0xFF;
                        int r = (v >>> 16) & 0xFF;
                        int g = (v >>> 8) & 0xFF;
                        int b = v & 0xFF;
                        writer.putByte((byte) b);
                        writer.putByte((byte) g);
                        writer.putByte((byte) r);
                        writer.putByte((byte) a);
                    }
                } else {
                    // 默认模式：保持原来的方式不变
                    for (int v : argb32) {
                        writer.putInt(v);
                    }
                }
                yield writer.output();
            }
            case WzPngFormat.ARGB1555 -> {
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB1555 + " 不支持 scale");
                }
                for (int v : argb32) {
                    writer.putShort(ImgTool.Argb32.toArgb1555(v));
                }
                yield writer.output();
            }
            case WzPngFormat.RGB565 -> {
                if (actualScale > 1) {
                    argb32 = ImgTool.Argb32.downscale(argb32, width, height, actualScale, true);
                }

                for (int v : argb32) {
                    writer.putShort(ImgTool.Argb32.toRgb565(v));
                }
                yield writer.output();
            }
            case WzPngFormat.DXT3 -> {
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB1555 + " 不支持 scale");
                }
                ImgTool.Argb32.toDXT3(img, writer);
                yield writer.output();
            }
            case WzPngFormat.DXT5 -> {
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.ARGB1555 + " 不支持 scale");
                }
                ImgTool.Argb32.toDXT5(img, writer);
                yield writer.output();
            }
            case WzPngFormat.BC7 -> {
                if (actualScale != 1) {
                    throw new IllegalArgumentException(WzPngFormat.BC7 + " 不支持 scale");
                }
                this.format = WzPngFormat.DXT5;
                ImgTool.Argb32.toDXT5(img, writer);
                log.warn("目前还不支持BC7编码, 已降级为 DXT5, 节点: {}", getPath());
                // ImgTool.Argb32.toBC7(img, writer);
                yield writer.output();
            }
            default -> throw new IllegalArgumentException("不支持的 PNG 格式用于写回: " + format);
        };
    }

    /**
     * 尝试用指定 zlib 策略压缩；若当前 JDK 不支持该策略（如部分环境不支持 RLE=3），返回 null。
     */
    private byte[] tryZlibCompress(byte[] rawBytes, int level, int strategy) {
        ByteArrayOutputStream memStream = new ByteArrayOutputStream();
        int lv = Math.max(Deflater.NO_COMPRESSION, Math.min(Deflater.BEST_COMPRESSION, level));
        Deflater deflater = new Deflater(lv, false);
        try {
            deflater.setStrategy(strategy);
        } catch (IllegalArgumentException ex) {
            // 例如 JDK 未实现或拒绝 Deflater 策略 3（RLE）时，消息可能为 "null"
            deflater.end();
            return null;
        }

        try (DeflaterOutputStream zip = new DeflaterOutputStream(memStream, deflater)) {
            zip.write(rawBytes);
        } catch (IOException e) {
            throw new RuntimeException("压缩失败", e);
        }

        return memStream.toByteArray();
    }

    private byte[] zlibCompress(byte[] rawBytes, int level, int strategy) {
        byte[] z = tryZlibCompress(rawBytes, level, strategy);
        if (z != null) {
            return z;
        }
        byte[] fallback = tryZlibCompress(rawBytes, level, Deflater.DEFAULT_STRATEGY);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("zlib 压缩失败：DEFAULT_STRATEGY 不可用");
    }

    private byte[] zlibCompressSmallest(byte[] rawBytes, int level) {
        byte[] best = null;
        for (int strat : WzPngZlibCompressMode.strategiesForBrute()) {
            byte[] z = tryZlibCompress(rawBytes, level, strat);
            if (z == null) {
                continue;
            }
            if (best == null || z.length < best.length) {
                best = z;
            }
        }
        return best != null ? best : zlibCompress(rawBytes, level, Deflater.DEFAULT_STRATEGY);
    }

    private byte[] zlibCompressOnly(byte[] rawBytes, int zlibLevel, WzPngZlibCompressMode zlibMode) {
        if (zlibMode.brutePickSmallest()) {
            return zlibCompressSmallest(rawBytes, zlibLevel);
        }
        return zlibCompress(rawBytes, zlibLevel, zlibMode.deflaterStrategy());
    }

    /**
     * 旧版技能分块长度（小端 int）的前若干字节不能与裸 zlib 头混淆，否则 {@link #peekListWzUsedFromBlob} 会误判。
     */
    private static boolean legacySkillChunkLengthPrefixMisreadAsPlainZlib(int chunkSizeLe) {
        byte b0 = (byte) chunkSizeLe;
        byte b1 = (byte) (chunkSizeLe >> 8);
        byte b2 = (byte) (chunkSizeLe >> 16);
        byte b3 = (byte) (chunkSizeLe >> 24);
        int header16 = ((b1 & 0xFF) << 8) | (b0 & 0xFF);
        if (legacyPlainZlibMagicUshort(header16)) {
            return true;
        }
        byte[] head4 = {b0, b1, b2, b3};
        return looksLikeRawZlibRfc1950(head4, 0);
    }

    private static int nextSafeLegacySkillChunkSize(int remaining) {
        int n = Math.min(remaining, 65535);
        while (n > 1 && legacySkillChunkLengthPrefixMisreadAsPlainZlib(n)) {
            n--;
        }
        if (n <= 0) {
            throw new IllegalStateException("无法选择不与 zlib 头冲突的旧版技能分块大小");
        }
        return n;
    }

    private byte[] wrapZlibPayloadWithLegacySkillChunks(byte[] zlibPayload) {
        WzMutableKey oldKey = new WzMutableKey(
                orange.wz.provider.tools.CryptoConstants.WZ_MSEAIV,
                orange.wz.provider.tools.CryptoConstants.USER_KEY);
        BinaryWriter writer = new BinaryWriter();
        int remaining = zlibPayload.length;
        int offset = 0;
        while (remaining > 0) {
            int chunkSize = nextSafeLegacySkillChunkSize(remaining);
            writer.putInt(chunkSize);
            for (int i = 0; i < chunkSize; i++) {
                writer.putByte((byte) (zlibPayload[offset + i] ^ oldKey.get(i)));
            }
            remaining -= chunkSize;
            offset += chunkSize;
        }
        return writer.output();
    }

    private static byte[] getRawBytesArgb8888Ordered(BufferedImage img, boolean legacyBgraOrder) {
        int[] argb32 = ImgTool.Argb32.fromBufferedImage(img);
        BinaryWriter writer = new BinaryWriter(false);
        if (legacyBgraOrder) {
            for (int v : argb32) {
                int a = (v >>> 24) & 0xFF;
                int r = (v >>> 16) & 0xFF;
                int g = (v >>> 8) & 0xFF;
                int b = v & 0xFF;
                writer.putByte((byte) b);
                writer.putByte((byte) g);
                writer.putByte((byte) r);
                writer.putByte((byte) a);
            }
        } else {
            for (int v : argb32) {
                writer.putInt(v);
            }
        }
        return writer.output();
    }

    /**
     * 在「已写入标准封装」之后，按菜单「旧版技能特效」将当前节点重新打包为 BGRA 原始字节序 + zlib + 固定密钥分块异或。
     * 供强力压缩等流程在跳过首次旧版路径后调用。
     */
    public void applyLegacySkillEffectAfterStrongCompress(int zlibLevel, WzPngZlibCompressMode zlibMode) {
        boolean legacy = false;
        try {
            legacy = orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption();
        } catch (Exception e) {
            return;
        }
        if (!legacy) {
            return;
        }
        if (wzImage == null || wzImage.getReader() == null) {
            log.warn("旧版技能二次打包跳过（无 Reader） {}", getPath());
            return;
        }
        WzMutableKey wzMutableKey = wzImage.getReader().getWzMutableKey();
        byte[] stored = getCompressedBytes(true);
        if (stored == null || stored.length == 0) {
            return;
        }
        try {
            byte[] zlibPayload;
            if (format == WzPngFormat.ARGB8888) {
                BufferedImage img = getImage(true);
                if (img == null) {
                    log.warn("旧版技能二次打包跳过（无法解码） {}", getPath());
                    return;
                }
                byte[] raw = getRawBytesArgb8888Ordered(img, true);
                zlibPayload = zlibCompressOnly(raw, zlibLevel, zlibMode);
            } else {
                zlibPayload = peelToZlibPayload(stored, wzMutableKey);
            }
            compressedBytes = wrapZlibPayloadWithLegacySkillChunks(zlibPayload);
            applyListWzUsedFromPngHeader(compressedBytes);
            clearImage();
        } catch (Exception e) {
            log.error("旧版技能特效二次打包失败（保留首次压缩结果） {}", getPath(), e);
        }
    }

    private void compressImage(int zlibLevel, WzPngZlibCompressMode zlibMode) {
        WzMutableKey wzMutableKey = wzImage.getReader().getWzMutableKey();
        width = image.getWidth();
        height = image.getHeight();

        byte[] rawBytes = getRawBytes(image, format);
        compressBytes(rawBytes, wzMutableKey, zlibLevel, zlibMode);
    }

    private void compressBytes(byte[] rawBytes, WzMutableKey wzMutableKey) {
        compressBytes(rawBytes, wzMutableKey, Deflater.DEFAULT_COMPRESSION, WzPngZlibCompressMode.DEFAULT);
    }

    private void compressBytes(byte[] rawBytes, WzMutableKey wzMutableKey, int zlibLevel, WzPngZlibCompressMode zlibMode) {
        /*
         * 加密规则（与菜单「工具 → 旧版技能特效」对应关系）：
         * 1) 勾选「旧版技能特效」：zlib 之后<strong>总是</strong>用固定旧密钥（WZ_MSEAIV + USER_KEY）做分块异或，
         *    与是否原为 List.wz 包头无关；ARGB8888 原始像素另见 getRawBytes 中的 BGRA 分支。
         * 2) 未勾选：仅当本节点在载入时包头判定为 List.wz 类异或（listWzUsed=true）时，才用<strong>当前 WZ Reader</strong>
         *    的 WzMutableKey 做单块异或，以匹配 Nexon/List.wz 等资源的打包方式；否则输出纯 zlib。
         * 三种情况互斥：旧版技能优先；否则按原图是否 List 异或决定；再否则不二次加密。
         */
        final boolean packagingWasNexonListXor = listWzUsed;

        if (zlibMode.brutePickSmallest()) {
            compressedBytes = zlibCompressSmallest(rawBytes, zlibLevel);
        } else {
            compressedBytes = zlibCompress(rawBytes, zlibLevel, zlibMode.deflaterStrategy());
        }

        boolean legacySkillEffectMode = false;
        if (!isSkipLegacySkillEffectForThisCompress()) {
            try {
                legacySkillEffectMode = orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption();
            } catch (Exception e) {
                // 无 GUI（如测试）时视为未勾选
            }
        }

        if (legacySkillEffectMode) {
            compressedBytes = wrapZlibPayloadWithLegacySkillChunks(compressedBytes);
        } else if (packagingWasNexonListXor) {
            BinaryWriter writer = new BinaryWriter();
            writer.setWzMutableKey(wzMutableKey);
            writer.putInt(compressedBytes.length);
            for (int i = 0; i < compressedBytes.length; i++) {
                writer.putByte((byte) (compressedBytes[i] ^ wzMutableKey.get(i)));
            }
            compressedBytes = writer.output();
        }
        applyListWzUsedFromPngHeader(compressedBytes);
    }

    public byte[] getCompressedBytes(boolean saveInMem) {
        if (compressedBytes == null) {
            byte[] returnBytes = null;
            if (offset != 0) {
                BinaryReader reader = wzImage.getReader();
                int curOffset = reader.getPosition();
                reader.setPosition(offset);
                int len = reader.getInt() - 1;
                reader.skip(1); // 跳过1个字节
                if (len > 0) {
                    returnBytes = reader.getBytes(len);
                }
                reader.setPosition(curOffset);

                if (returnBytes != null) {
                    applyListWzUsedFromPngHeader(returnBytes);
                }

                if (saveInMem) {
                    compressedBytes = returnBytes;
                }
            } else if (image != null) {
                compressImage(Deflater.DEFAULT_COMPRESSION, WzPngZlibCompressMode.DEFAULT);
                returnBytes = compressedBytes;
                if (!saveInMem) {
                    compressedBytes = null;
                }
            }
            return returnBytes;
        }
        applyListWzUsedFromPngHeader(compressedBytes);
        return compressedBytes;
    }

    public void rebuildCompressedBytesUseNewWzKey(WzMutableKey wzMutableKey) {
        // 该方法在处理CMS079的Map.wz时要额外花费123秒，只是为了List.wz的图片
        byte[] compressedBytes = getCompressedBytes(false);
        byte[] rawBytes = decompress(compressedBytes);
        if (listWzUsed) {
            compressBytes(rawBytes, wzMutableKey);
        }
    }

    private static BufferedImage deepClone(BufferedImage src) {
        ColorModel cm = src.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = src.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    // Override --------------------------------------------------------------------------------------------------------
    @Override
    public void writeValue(BinaryWriter writer) {
        throw new RuntimeException("WzPngProperty writeValue不能单独调用");
    }

    @Override
    public WzPngProperty deepClone(WzObject parent) {
        WzPngProperty clone = new WzPngProperty(name, parent, null);
        clone.width = width;
        clone.height = height;
        clone.format = format;
        clone.scale = scale;
        // getCompressedBytes 可能首次从 reader 拉取数据并据 zlib 头刷新 listWzUsed；必须先调用再拷贝标志，否则会误判为非 List.wz 加密导致解压时空密钥 NPE
        byte[] srcCompressed = getCompressedBytes(false);
        clone.listWzUsed = listWzUsed;
        if (srcCompressed != null) {
            clone.compressedBytes = Arrays.copyOf(srcCompressed, srcCompressed.length);
        }
        if (image != null) {
            clone.image = deepClone(image);
        }

        return clone;
    }
}
