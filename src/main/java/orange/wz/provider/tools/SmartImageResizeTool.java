package orange.wz.provider.tools;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

public final class SmartImageResizeTool {
    private SmartImageResizeTool() {
    }

    /**
     * 先按目标宽度等比缩放，再按目标高度进行上下居中裁剪（不足时上下居中留白）。
     * 整个过程始终保持比例，不拉伸、不偏位。
     */
    public static BufferedImage resizeByWidthAndCenterCrop(BufferedImage source, int targetWidth, int targetHeight) {
        if (source == null) {
            throw new IllegalArgumentException("source image is null");
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("target size must be positive");
        }

        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }

        double widthScale = (double) targetWidth / source.getWidth();
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * widthScale));

        BufferedImage scaled = widthScale >= 1.0
                ? scaleUpWithSharpen(source, targetWidth, scaledHeight)
                : progressiveDownscale(source, targetWidth, scaledHeight);

        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (scaledHeight >= targetHeight) {
            int srcY = (scaledHeight - targetHeight) / 2;
            g.drawImage(scaled, 0, 0, targetWidth, targetHeight, 0, srcY, targetWidth, srcY + targetHeight, null);
        } else {
            int drawY = (targetHeight - scaledHeight) / 2;
            g.drawImage(scaled, 0, drawY, null);
        }
        g.dispose();
        return output;
    }

    private static BufferedImage scaleUpWithSharpen(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();

        float[] sharpen = {
                0f, -0.2f, 0f,
                -0.2f, 1.8f, -0.2f,
                0f, -0.2f, 0f
        };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, sharpen), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(scaled, null);
    }

    /**
     * 逐步缩小，减少一次性缩放带来的细节丢失和锯齿。
     */
    private static BufferedImage progressiveDownscale(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
            width /= 2;
            height /= 2;
            current = drawScaled(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return drawScaled(current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage drawScaled(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return output;
    }
}
