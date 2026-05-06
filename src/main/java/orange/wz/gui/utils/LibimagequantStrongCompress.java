package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import org.pngquant.Image;
import org.pngquant.PngQuant;
import org.pngquant.PngQuantException;
import org.pngquant.Result;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * 使用 <a href="https://github.com/Lcry/java-png-compress-util">java-png-compress-util</a> 中的
 * {@code org.pngquant} JNI 封装（与 libimagequant 2.x 配套），将图像量化为调色板后再展开为
 * {@link BufferedImage#TYPE_INT_ARGB}，供强力压缩写入 WZ PNG。
 */
@Slf4j
public final class LibimagequantStrongCompress {

    private LibimagequantStrongCompress() {
    }

    /**
     * 当前平台在 classpath 中是否带有对应的原生库资源（不触发 JNI 加载）。
     */
    public static boolean isRuntimeAvailable() {
        String path = nativeClasspathResource();
        if (path == null) {
            return false;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(path) : null;
        if (in == null) {
            in = LibimagequantStrongCompress.class.getClassLoader().getResourceAsStream(path);
        }
        if (in == null) {
            return false;
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
        return true;
    }

    private static String nativeClasspathResource() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "libimagequant/libimagequant.dll";
        }
        if (os.contains("mac")) {
            return "libimagequant/libimagequant.jnilib";
        }
        if (os.contains("linux")) {
            return "libimagequant/libimagequant.so";
        }
        return null;
    }

    /**
     * 将任意 {@link BufferedImage} 经 libimagequant 量化后转为 {@link BufferedImage#TYPE_INT_ARGB}。
     * 量化失败时返回与输入同尺寸的 ARGB 拷贝，避免中断批量任务。
     * <p>
     * 说明：{@code PngQuant.quantize} 返回 {@code null} 表示在<strong>当前质量下限</strong>下无法用
     * 给定色数完成量化（libimagequant 会抛 {@link PngQuantException} 并被封装类吞掉）。
     * DXT5 等解码后细节、渐变多，容易在默认质量 {@code 65–80} 下失败；本实现会自动再试 {@code 0–100}。
     */
    public static BufferedImage quantizeToArgb8888(BufferedImage src, int maxColors, int speed, float ditherLevel) {
        if (src == null) {
            throw new IllegalArgumentException("src == null");
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("空图像");
        }

        int colors = Math.max(2, Math.min(256, maxColors));
        int spd = Math.max(1, Math.min(11, speed));
        float dither = Math.max(0f, Math.min(1f, ditherLevel));

        int[][] qualityRanges = {{65, 80}, {0, 100}};

        PngQuant q = new PngQuant();
        try {
            q.setMaxColors(colors);
            q.setSpeed(spd);

            for (int i = 0; i < qualityRanges.length; i++) {
                int qmin = qualityRanges[i][0];
                int qmax = qualityRanges[i][1];
                q.setQuality(qmin, qmax);

                final Image liqimg;
                try {
                    liqimg = new Image(q, src);
                } catch (PngQuantException e) {
                    log.warn("libimagequant 构造 Image 失败: {}", e.getMessage());
                    return copyArgb(src);
                }
                try {
                    Result result = q.quantize(liqimg);
                    if (result == null) {
                        if (i + 1 < qualityRanges.length) {
                            log.info("libimagequant 在质量 {}–{} 下无法量化（色数/细节限制），将放宽质量重试", qmin, qmax);
                            continue;
                        }
                        log.warn("libimagequant 在放宽质量后仍无法量化（常见于 DXT5 等贴图），跳过调色板量化，仅按目标格式/ZLIB 重编码");
                        return copyArgb(src);
                    }
                    try {
                        result.setDitheringLevel(dither);
                        BufferedImage indexed = result.getRemapped(liqimg);
                        if (indexed == null) {
                            log.warn("libimagequant getRemapped 返回 null");
                            return copyArgb(src);
                        }
                        return indexedToArgb(indexed);
                    } finally {
                        result.close();
                    }
                } finally {
                    liqimg.close();
                }
            }
        } finally {
            q.close();
        }
        return copyArgb(src);
    }

    private static BufferedImage copyArgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, source.getWidth(), source.getHeight(),
                source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth()),
                0, source.getWidth());
        return copy;
    }

    private static BufferedImage indexedToArgb(BufferedImage indexed) {
        int w = indexed.getWidth();
        int h = indexed.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(indexed, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
