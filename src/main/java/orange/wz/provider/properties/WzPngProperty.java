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
        image = null;
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

    // Decompress ------------------------------------------------------------------------------------------------------
    private InflaterInputStream createZlibStream(byte[] compressedBytes, WzMutableKey wzMutableKey) {
        // C# CompressionMode.Decompress -> Java InflaterInputStream
        // C# CompressionMode.Compress -> Java DeflaterOutputStream
        InflaterInputStream zlib;

        BinaryReader reader = new BinaryReader(compressedBytes);
        int header = reader.getShort() & 0xFFFF;  // 读取无符号短整型
        // CMS079 header = 0x5E78 是ListWzUsed // acc6.img/folkvillige/moon1/19/0/0
        listWzUsed = header != 0x9C78 && header != 0xDA78 && header != 0x0178;
        if (!listWzUsed) {
            zlib = new InflaterInputStream(new ByteArrayInputStream(compressedBytes));
        } else {
            reader.setPosition(0);
            BinaryWriter writer = new BinaryWriter();

            while (reader.hasRemaining()) {
                int blockSize = reader.getInt();
                for (int i = 0; i < blockSize; i++) {
                    writer.putByte((byte) (reader.getByte() ^ wzMutableKey.get(i)));
                }
            }
            zlib = new InflaterInputStream(new ByteArrayInputStream(writer.output()));
        }

        return zlib;
    }

    private byte[] decompress(byte[] compressedBytes) {
        int size = ImgTool.getRawByteSize(format, getActualScale(), width, height);
        byte[] rawBytes = new byte[size]; // decompress byte
        WzMutableKey wzMutableKey = null;
        if (listWzUsed) {
            if (wzImage == null || wzImage.getReader() == null) {
                throw new RuntimeException("listWz 图片解密失败：缺少 WzReader 上下文");
            }
            wzMutableKey = wzImage.getReader().getWzMutableKey();
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
                try {
                    useOldEnc = orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption();
                } catch (Exception e) {
                    // ignore if MainFrame is not available
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
        if (zlibMode.brutePickSmallest()) {
            compressedBytes = zlibCompressSmallest(rawBytes, zlibLevel);
        } else {
            compressedBytes = zlibCompress(rawBytes, zlibLevel, zlibMode.deflaterStrategy());
        }
        // 检查是否使用旧版技能特效加密
        boolean useOldEnc = false;
        try {
            useOldEnc = orange.wz.gui.MainFrame.getInstance().isUseOldSkillEncryption();
        } catch (Exception e) {
            // ignore if MainFrame is not available
        }

        if (useOldEnc) {
            // 旧版技能特效模式：使用 listWz 格式的加密
            WzMutableKey oldKey = new WzMutableKey(orange.wz.provider.tools.CryptoConstants.WZ_MSEAIV, orange.wz.provider.tools.CryptoConstants.USER_KEY);
            
            BinaryWriter writer = new BinaryWriter();
            int remaining = compressedBytes.length;
            int offset = 0;
            while (remaining > 0) {
                int chunkSize = Math.min(remaining, 65535); // 每个块最大 65535 字节
                writer.putInt(chunkSize);
                for (int i = 0; i < chunkSize; i++) {
                    writer.putByte((byte) (compressedBytes[offset + i] ^ oldKey.get(i)));
                }
                remaining -= chunkSize;
                offset += chunkSize;
            }
            compressedBytes = writer.output();
        } else if (listWzUsed) {
            // 默认模式：保持原来的方式不变，只根据 listWzUsed 标志
            BinaryWriter writer = new BinaryWriter();
            writer.setWzMutableKey(wzMutableKey);
            writer.putInt(compressedBytes.length);
            for (int i = 0; i < compressedBytes.length; i++) {
                writer.putByte((byte) (compressedBytes[i] ^ wzMutableKey.get(i)));
            }
            compressedBytes = writer.output();
        }
        // 否则（没有 listWzUsed）：保持压缩后的原始 bytes，和原来一样
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

                // 确保 listWzUsed 被正确设置
                if (returnBytes != null && returnBytes.length >= 2) {
                    int header = ((returnBytes[1] & 0xFF) << 8) | (returnBytes[0] & 0xFF);
                    listWzUsed = header != 0x9C78 && header != 0xDA78 && header != 0x0178;
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
