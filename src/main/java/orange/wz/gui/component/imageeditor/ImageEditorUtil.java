package orange.wz.gui.component.imageeditor;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public final class ImageEditorUtil {

    private ImageEditorUtil() {
    }

    public static BufferedImage createBlankImage(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage toArgb(BufferedImage src) {
        if (src == null) {
            return null;
        }
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public static BufferedImage deepCopy(BufferedImage src) {
        if (src == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    public static BufferedImage copyRegion(BufferedImage src, Rectangle region) {
        Rectangle r = clampRect(region, src.getWidth(), src.getHeight());
        if (r == null) {
            return null;
        }
        BufferedImage sub = src.getSubimage(r.x, r.y, r.width, r.height);
        return deepCopy(sub);
    }

    public static Rectangle clampRect(Rectangle rect, int maxW, int maxH) {
        if (rect == null || rect.isEmpty()) {
            return null;
        }
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int w = Math.min(rect.width, maxW - x);
        int h = Math.min(rect.height, maxH - y);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new Rectangle(x, y, w, h);
    }

    public static BufferedImage rotateImage(BufferedImage src, double degrees) {
        double radians = Math.toRadians(degrees);
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform transform = new AffineTransform();
        transform.rotate(radians, w / 2.0, h / 2.0);

        Rectangle bounds = transform.createTransformedShape(new Rectangle(0, 0, w, h)).getBounds();
        BufferedImage out = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-bounds.x, -bounds.y);
        g.rotate(radians, w / 2.0, h / 2.0);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * 变换选区内容并返回与变换后像素形状一致的新选区。
     */
    public static ImageEditorSelection transformRegion(BufferedImage image, ImageEditorSelection selection,
                                                       java.util.function.Function<BufferedImage, BufferedImage> transform) {
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        if (selection == null || selection.isEmpty()) {
            BufferedImage result = transform.apply(deepCopy(image));
            Graphics2D g = image.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(result, 0, 0, null);
            g.dispose();
            return ImageEditorSelection.rectangle(new Rectangle(0, 0, imgW, imgH), imgW, imgH);
        }

        Rectangle bounds = selection.getBounds();
        BufferedImage sub = extractMaskedSubimage(image, selection);
        clearSelectionPixels(image, selection);

        BufferedImage transformed = transform.apply(sub);
        int origCx = bounds.x + bounds.width / 2;
        int origCy = bounds.y + bounds.height / 2;
        int destX = origCx - transformed.getWidth() / 2;
        int destY = origCy - transformed.getHeight() / 2;

        boolean[] mask = new boolean[imgW * imgH];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int y = 0; y < transformed.getHeight(); y++) {
            for (int x = 0; x < transformed.getWidth(); x++) {
                int argb = transformed.getRGB(x, y);
                if (((argb >> 24) & 0xFF) == 0) {
                    continue;
                }
                int ix = destX + x;
                int iy = destY + y;
                if (ix < 0 || iy < 0 || ix >= imgW || iy >= imgH) {
                    continue;
                }
                image.setRGB(ix, iy, argb);
                mask[iy * imgW + ix] = true;
                minX = Math.min(minX, ix);
                minY = Math.min(minY, iy);
                maxX = Math.max(maxX, ix);
                maxY = Math.max(maxY, iy);
            }
        }

        if (minX > maxX) {
            return null;
        }
        Rectangle newBounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return ImageEditorSelection.fromMask(imgW, imgH, mask, newBounds);
    }

    public static ImageEditorSelection rotateRegion(BufferedImage image, ImageEditorSelection selection, double degrees) {
        return transformRegion(image, selection, sub -> rotateImage(sub, degrees));
    }

    public static ImageEditorSelection flipHorizontal(BufferedImage image, ImageEditorSelection selection) {
        return transformRegion(image, selection, sub -> {
            BufferedImage flipped = new BufferedImage(sub.getWidth(), sub.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = flipped.createGraphics();
            g.drawImage(sub, sub.getWidth(), 0, 0, sub.getHeight(), 0, 0, sub.getWidth(), sub.getHeight(), null);
            g.dispose();
            return flipped;
        });
    }

    public static ImageEditorSelection flipVertical(BufferedImage image, ImageEditorSelection selection) {
        return transformRegion(image, selection, sub -> {
            BufferedImage flipped = new BufferedImage(sub.getWidth(), sub.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = flipped.createGraphics();
            g.drawImage(sub, 0, sub.getHeight(), sub.getWidth(), 0, 0, 0, sub.getWidth(), sub.getHeight(), null);
            g.dispose();
            return flipped;
        });
    }

    private static BufferedImage extractMaskedSubimage(BufferedImage image, ImageEditorSelection selection) {
        Rectangle bounds = selection.getBounds();
        BufferedImage sub = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                int ix = bounds.x + x;
                int iy = bounds.y + y;
                if (selection.contains(ix, iy)) {
                    sub.setRGB(x, y, image.getRGB(ix, iy));
                }
            }
        }
        return sub;
    }

    public static void adjustRgb(BufferedImage image, ImageEditorSelection selection, int deltaR, int deltaG, int deltaB) {
        forEachPixel(image, selection, (x, y, argb) -> {
            int a = (argb >> 24) & 0xFF;
            int red = clamp((argb >> 16) & 0xFF, deltaR);
            int green = clamp((argb >> 8) & 0xFF, deltaG);
            int blue = clamp(argb & 0xFF, deltaB);
            image.setRGB(x, y, (a << 24) | (red << 16) | (green << 8) | blue);
        });
    }

    public static void setRgb(BufferedImage image, ImageEditorSelection selection, int red, int green, int blue) {
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));
        int finalRed = red;
        int finalGreen = green;
        int finalBlue = blue;
        forEachPixel(image, selection, (x, y, argb) -> {
            int a = (argb >> 24) & 0xFF;
            image.setRGB(x, y, (a << 24) | (finalRed << 16) | (finalGreen << 8) | finalBlue);
        });
    }

    public static void adjustHsv(BufferedImage image, ImageEditorSelection selection, float deltaH, float deltaS, float deltaV) {
        float[] hsb = new float[3];
        forEachPixel(image, selection, (x, y, argb) -> {
            int a = (argb >> 24) & 0xFF;
            Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
            hsb[0] = (hsb[0] + deltaH / 360f) % 1f;
            if (hsb[0] < 0) {
                hsb[0] += 1f;
            }
            hsb[1] = Math.max(0f, Math.min(1f, hsb[1] + deltaS / 100f));
            hsb[2] = Math.max(0f, Math.min(1f, hsb[2] + deltaV / 100f));
            int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            image.setRGB(x, y, (a << 24) | (rgb & 0xFFFFFF));
        });
    }

    public static void adjustAlpha(BufferedImage image, ImageEditorSelection selection, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        int finalAlpha = alpha;
        forEachPixel(image, selection, (x, y, argb) ->
                image.setRGB(x, y, (finalAlpha << 24) | (argb & 0xFFFFFF)));
    }

    public static void applyMosaic(BufferedImage image, ImageEditorSelection selection, int blockSize) {
        blockSize = Math.max(2, blockSize);
        Rectangle bounds = selection == null || selection.isEmpty()
                ? new Rectangle(0, 0, image.getWidth(), image.getHeight())
                : selection.getBounds();
        if (bounds == null) {
            return;
        }
        int endX = bounds.x + bounds.width;
        int endY = bounds.y + bounds.height;
        for (int by = bounds.y; by < endY; by += blockSize) {
            for (int bx = bounds.x; bx < endX; bx += blockSize) {
                int bw = Math.min(blockSize, endX - bx);
                int bh = Math.min(blockSize, endY - by);
                long sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                int count = 0;
                for (int y = by; y < by + bh; y++) {
                    for (int x = bx; x < bx + bw; x++) {
                        if (selection != null && !selection.isEmpty() && !selection.contains(x, y)) {
                            continue;
                        }
                        int argb = image.getRGB(x, y);
                        sumA += (argb >> 24) & 0xFF;
                        sumR += (argb >> 16) & 0xFF;
                        sumG += (argb >> 8) & 0xFF;
                        sumB += argb & 0xFF;
                        count++;
                    }
                }
                if (count == 0) {
                    continue;
                }
                int avg = ((int) (sumA / count) << 24)
                        | ((int) (sumR / count) << 16)
                        | ((int) (sumG / count) << 8)
                        | (int) (sumB / count);
                for (int y = by; y < by + bh; y++) {
                    for (int x = bx; x < bx + bw; x++) {
                        if (selection != null && !selection.isEmpty() && !selection.contains(x, y)) {
                            continue;
                        }
                        image.setRGB(x, y, avg);
                    }
                }
            }
        }
    }

    public static ImageEditorSelection magicWandSelect(BufferedImage image, int startX, int startY, int tolerance) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (startX < 0 || startY < 0 || startX >= w || startY >= h) {
            return null;
        }
        int seed = image.getRGB(startX, startY);
        boolean[] mask = new boolean[w * h];
        boolean[] visited = new boolean[w * h];
        int[] stack = new int[w * h * 2];
        int top = 0;
        stack[top++] = startX;
        stack[top++] = startY;
        int minX = startX, minY = startY, maxX = startX, maxY = startY;

        while (top > 0) {
            int y = stack[--top];
            int x = stack[--top];
            int idx = y * w + x;
            if (x < 0 || y < 0 || x >= w || y >= h || visited[idx]) {
                continue;
            }
            if (!magicWandMatch(seed, image.getRGB(x, y), tolerance)) {
                continue;
            }
            visited[idx] = true;
            mask[idx] = true;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            stack[top++] = x + 1;
            stack[top++] = y;
            stack[top++] = x - 1;
            stack[top++] = y;
            stack[top++] = x;
            stack[top++] = y + 1;
            stack[top++] = x;
            stack[top++] = y - 1;
        }
        if (minX > maxX) {
            return null;
        }
        Rectangle bounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return ImageEditorSelection.fromMask(w, h, mask, bounds);
    }

    /** 构建选区像素轮廓路径（图像坐标，单位像素） */
    public static GeneralPath buildSelectionOutline(ImageEditorSelection sel) {
        GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO);
        if (sel == null || sel.isEmpty()) {
            return path;
        }
        Rectangle b = sel.getBounds();
        for (int y = b.y; y < b.y + b.height; y++) {
            for (int x = b.x; x < b.x + b.width; x++) {
                if (!sel.contains(x, y)) {
                    continue;
                }
                if (!sel.contains(x, y - 1)) {
                    path.moveTo(x, y);
                    path.lineTo(x + 1, y);
                }
                if (!sel.contains(x + 1, y)) {
                    path.moveTo(x + 1, y);
                    path.lineTo(x + 1, y + 1);
                }
                if (!sel.contains(x, y + 1)) {
                    path.moveTo(x + 1, y + 1);
                    path.lineTo(x, y + 1);
                }
                if (!sel.contains(x - 1, y)) {
                    path.moveTo(x, y + 1);
                    path.lineTo(x, y);
                }
            }
        }
        return path;
    }

    /** 清除选区像素（移动选区时使用） */
    public static void clearSelectionPixels(BufferedImage image, ImageEditorSelection selection) {
        if (selection == null || selection.isEmpty()) {
            return;
        }
        Rectangle b = selection.getBounds();
        for (int y = b.y; y < b.y + b.height; y++) {
            for (int x = b.x; x < b.x + b.width; x++) {
                if (selection.contains(x, y)) {
                    int argb = image.getRGB(x, y);
                    image.setRGB(x, y, argb & 0x00FFFFFF);
                }
            }
        }
    }

    /** 将浮动图层合成到主图 */
    public static void compositeFloating(BufferedImage image, BufferedImage floating, int destX, int destY,
                                         ImageEditorSelection sourceMask) {
        if (floating == null || sourceMask == null || sourceMask.isEmpty()) {
            return;
        }
        Rectangle b = sourceMask.getBounds();
        for (int ly = 0; ly < floating.getHeight(); ly++) {
            for (int lx = 0; lx < floating.getWidth(); lx++) {
                int sx = b.x + lx;
                int sy = b.y + ly;
                if (!sourceMask.contains(sx, sy)) {
                    continue;
                }
                int argb = floating.getRGB(lx, ly);
                if (((argb >> 24) & 0xFF) == 0) {
                    continue;
                }
                int tx = destX + lx;
                int ty = destY + ly;
                if (tx >= 0 && ty >= 0 && tx < image.getWidth() && ty < image.getHeight()) {
                    image.setRGB(tx, ty, argb);
                }
            }
        }
    }

    public static BufferedImage extractSelection(BufferedImage image, ImageEditorSelection selection) {
        if (selection == null || selection.isEmpty()) {
            return deepCopy(image);
        }
        Rectangle b = selection.getBounds();
        BufferedImage out = new BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < b.height; y++) {
            for (int x = 0; x < b.width; x++) {
                int ix = b.x + x;
                int iy = b.y + y;
                if (selection.contains(ix, iy)) {
                    out.setRGB(x, y, image.getRGB(ix, iy));
                }
            }
        }
        return out;
    }

    public static void pasteImage(BufferedImage image, BufferedImage clip, int x, int y, ImageEditorSelection limit) {
        if (clip == null) {
            return;
        }
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        for (int cy = 0; cy < clip.getHeight(); cy++) {
            for (int cx = 0; cx < clip.getWidth(); cx++) {
                int ix = x + cx;
                int iy = y + cy;
                if (ix < 0 || iy < 0 || ix >= image.getWidth() || iy >= image.getHeight()) {
                    continue;
                }
                if (limit != null && !limit.isEmpty() && !limit.contains(ix, iy)) {
                    continue;
                }
                int argb = clip.getRGB(cx, cy);
                if (((argb >> 24) & 0xFF) > 0) {
                    g.setColor(new Color(argb, true));
                    g.fillRect(ix, iy, 1, 1);
                }
            }
        }
        g.dispose();
    }

    public static BufferedImage resizeCanvas(BufferedImage src, int newW, int newH) {
        newW = Math.max(1, newW);
        newH = Math.max(1, newH);
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public static BufferedImage resizeImage(BufferedImage src, int newW, int newH) {
        newW = Math.max(1, newW);
        newH = Math.max(1, newH);
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return out;
    }

    public static Color pickColor(BufferedImage image, int x, int y) {
        if (image == null || x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return Color.BLACK;
        }
        return new Color(image.getRGB(x, y), true);
    }

    public static void floodFill(BufferedImage image, int startX, int startY, Color fillColor,
                                 int tolerance, ImageEditorSelection limit) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (startX < 0 || startY < 0 || startX >= w || startY >= h) {
            return;
        }
        if (limit != null && !limit.isEmpty() && !limit.contains(startX, startY)) {
            return;
        }
        int target = image.getRGB(startX, startY);
        int fill = fillColor.getRGB();
        if (fillColorsMatch(target, fill, tolerance)) {
            return;
        }
        if (limit == null || limit.isEmpty()) {
            if (isTransparentPixel(target, tolerance)) {
                fillAllTransparent(image, fill, tolerance);
                return;
            }
        }
        boolean[] visited = new boolean[w * h];
        int[] stack = new int[w * h * 2];
        int top = 0;
        stack[top++] = startX;
        stack[top++] = startY;

        while (top > 0) {
            int y = stack[--top];
            int x = stack[--top];
            int idx = y * w + x;
            if (x < 0 || y < 0 || x >= w || y >= h || visited[idx]) {
                continue;
            }
            if (limit != null && !limit.isEmpty() && !limit.contains(x, y)) {
                continue;
            }
            if (!fillColorsMatch(image.getRGB(x, y), target, tolerance)) {
                continue;
            }
            visited[idx] = true;
            image.setRGB(x, y, fill);
            stack[top++] = x + 1;
            stack[top++] = y;
            stack[top++] = x - 1;
            stack[top++] = y;
            stack[top++] = x;
            stack[top++] = y + 1;
            stack[top++] = x;
            stack[top++] = y - 1;
        }
    }

    private static void fillAllTransparent(BufferedImage image, int fillArgb, int tolerance) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isTransparentPixel(image.getRGB(x, y), tolerance)) {
                    image.setRGB(x, y, fillArgb);
                }
            }
        }
    }

    private static boolean isTransparentPixel(int argb, int tolerance) {
        int alpha = (argb >> 24) & 0xFF;
        return alpha <= Math.max(1, tolerance / 8);
    }

    private static boolean fillColorsMatch(int c1, int c2, int tolerance) {
        if (isTransparentPixel(c1, tolerance) && isTransparentPixel(c2, tolerance)) {
            return true;
        }
        int a1 = (c1 >> 24) & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        if (Math.abs(a1 - a2) > tolerance) {
            return false;
        }
        return magicWandMatch(c1, c2, tolerance);
    }

    public static void drawBrush(BufferedImage image, int cx, int cy, int radius, Color color,
                                 boolean erase, ImageEditorSelection limit) {
        int w = image.getWidth();
        int h = image.getHeight();
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > r2) {
                    continue;
                }
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= w || y >= h) {
                    continue;
                }
                if (limit != null && !limit.isEmpty() && !limit.contains(x, y)) {
                    continue;
                }
                if (erase) {
                    int argb = image.getRGB(x, y);
                    image.setRGB(x, y, argb & 0x00FFFFFF);
                } else {
                    int newArgb = color.getRGB();
                    int oldArgb = image.getRGB(x, y);
                    float alpha = color.getAlpha() / 255f;
                    image.setRGB(x, y, blendArgb(oldArgb, newArgb, alpha));
                }
            }
        }
    }

    public static void saveImage(File file, BufferedImage image, String format, float quality) throws IOException {
        String fmt = format.toLowerCase();
        if ("jpg".equals(fmt) || "jpeg".equals(fmt)) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            writeJpeg(rgb, file, quality);
            return;
        }
        if ("png".equals(fmt)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
                }
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);
                } finally {
                    writer.dispose();
                }
                return;
            }
        }
        ImageIO.write(image, fmt, file);
    }

    private interface PixelConsumer {
        void accept(int x, int y, int argb);
    }

    private static void forEachPixel(BufferedImage image, ImageEditorSelection selection, PixelConsumer consumer) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (selection == null || selection.isEmpty()) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    consumer.accept(x, y, image.getRGB(x, y));
                }
            }
            return;
        }
        Rectangle b = selection.getBounds();
        for (int y = b.y; y < b.y + b.height; y++) {
            for (int x = b.x; x < b.x + b.width; x++) {
                if (selection.contains(x, y)) {
                    consumer.accept(x, y, image.getRGB(x, y));
                }
            }
        }
    }

    private static void writeJpeg(BufferedImage image, File file, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", file);
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static int clamp(int value, int delta) {
        return Math.max(0, Math.min(255, value + delta));
    }

    private static boolean colorsMatch(int c1, int c2, int tolerance) {
        return magicWandMatch(c1, c2, tolerance);
    }

    /** 供魔棒等仅比较 RGB 的场景使用 */
    static boolean magicWandMatch(int sample, int pixel, int tolerance) {
        int r1 = (sample >> 16) & 0xFF;
        int g1 = (sample >> 8) & 0xFF;
        int b1 = sample & 0xFF;
        int r2 = (pixel >> 16) & 0xFF;
        int g2 = (pixel >> 8) & 0xFF;
        int b2 = pixel & 0xFF;
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        double dist = Math.sqrt(dr * dr + dg * dg + db * db);
        double limit = tolerance * 1.7320508;
        return dist <= limit;
    }

    private static int blendArgb(int bg, int fg, float alpha) {
        int ba = (bg >> 24) & 0xFF;
        int br = (bg >> 16) & 0xFF;
        int bG = (bg >> 8) & 0xFF;
        int bb = bg & 0xFF;
        int fa = (fg >> 24) & 0xFF;
        int fr = (fg >> 16) & 0xFF;
        int fG = (fg >> 8) & 0xFF;
        int fb = fg & 0xFF;
        float a = alpha * (fa / 255f);
        int outA = Math.round(ba * (1 - a) + fa * a);
        int outR = Math.round(br * (1 - a) + fr * a);
        int outG = Math.round(bG * (1 - a) + fG * a);
        int outB = Math.round(bb * (1 - a) + fb * a);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}
