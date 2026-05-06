package orange.wz.gui.video;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * ARGB8888 / ARGB4444 转换（4444：每通道保留高 4 位，再 ×17 展开为 0～255 写入 TYPE_INT_ARGB）。
 */
public final class VideoArgbFormatConverter {

    private VideoArgbFormatConverter() {
    }

    public static BufferedImage apply(BufferedImage src, VideoImageBitDepth depth) {
        if (src == null) {
            return null;
        }
        return switch (depth) {
            case ARGB8888 -> copyAsIntArgb(src);
            case ARGB4444 -> toArgb4444Quantized(src);
        };
    }

    private static BufferedImage copyAsIntArgb(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static BufferedImage toArgb4444Quantized(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage argb = copyAsIntArgb(src);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = argb.getRGB(x, y);
                int a = quant4((p >>> 24) & 0xff);
                int r = quant4((p >> 16) & 0xff);
                int g = quant4((p >> 8) & 0xff);
                int b = quant4(p & 0xff);
                dst.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return dst;
    }

    private static int quant4(int ch8) {
        int n = (ch8 >> 4) & 0xf;
        return n * 17;
    }

    /**
     * 是否存在非完全不透明的像素（用于统计「含透明度」张数）。
     */
    public static boolean hasNonFullyOpaqueAlpha(BufferedImage img) {
        if (img == null) {
            return false;
        }
        if (!img.getColorModel().hasAlpha()) {
            return false;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (img.getRGB(x, y) >>> 24) & 0xff;
                if (a < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    public static BufferedImage flattenOnWhite(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}
