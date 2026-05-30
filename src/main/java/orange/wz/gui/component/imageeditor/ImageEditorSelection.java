package orange.wz.gui.component.imageeditor;



import java.awt.*;

import java.util.Arrays;



/** 矩形或魔棒像素选区 */

public final class ImageEditorSelection {



    private final int width;

    private final int height;

    private final Rectangle bounds;

    private final boolean[] mask;

    private final boolean rectangular;



    private ImageEditorSelection(int width, int height, Rectangle bounds, boolean[] mask, boolean rectangular) {

        this.width = width;

        this.height = height;

        this.bounds = bounds;

        this.mask = mask;

        this.rectangular = rectangular;

    }



    public static ImageEditorSelection rectangle(Rectangle rect, int imageW, int imageH) {

        Rectangle b = ImageEditorUtil.clampRect(rect, imageW, imageH);

        if (b == null || b.isEmpty()) {

            return null;

        }

        return new ImageEditorSelection(imageW, imageH, b, null, true);

    }



    public static ImageEditorSelection fromMask(int imageW, int imageH, boolean[] mask, Rectangle bounds) {

        return new ImageEditorSelection(imageW, imageH, bounds, mask, false);

    }



    public boolean isEmpty() {

        return bounds == null || bounds.isEmpty();

    }



    public boolean isRectangular() {

        return rectangular;

    }



    public Rectangle getBounds() {

        return bounds == null ? null : new Rectangle(bounds);

    }



    public boolean contains(int x, int y) {

        if (x < 0 || y < 0 || x >= width || y >= height) {

            return false;

        }

        if (rectangular) {

            return bounds.contains(x, y);

        }

        return mask[y * width + x];

    }



    public boolean[] copyMask() {

        if (rectangular) {

            boolean[] full = new boolean[width * height];

            Rectangle b = bounds;

            for (int y = b.y; y < b.y + b.height; y++) {

                for (int x = b.x; x < b.x + b.width; x++) {

                    full[y * width + x] = true;

                }

            }

            return full;

        }

        return Arrays.copyOf(mask, mask.length);

    }



    public int getImageWidth() {

        return width;

    }



    public int getImageHeight() {

        return height;

    }



    public static ImageEditorSelection translated(ImageEditorSelection src, int dx, int dy) {

        if (src == null || src.isEmpty()) {

            return src;

        }

        Rectangle b = src.getBounds();

        Rectangle nb = new Rectangle(b.x + dx, b.y + dy, b.width, b.height);

        int w = src.getImageWidth();

        int h = src.getImageHeight();

        if (src.rectangular) {

            return rectangle(nb, w, h);

        }

        boolean[] mask = new boolean[w * h];

        for (int y = b.y; y < b.y + b.height; y++) {

            for (int x = b.x; x < b.x + b.width; x++) {

                if (src.contains(x, y)) {

                    int nx = x + dx;

                    int ny = y + dy;

                    if (nx >= 0 && ny >= 0 && nx < w && ny < h) {

                        mask[ny * w + nx] = true;

                    }

                }

            }

        }

        return fromMask(w, h, mask, nb);

    }



    public int countSelectedPixels() {

        if (rectangular) {

            return bounds.width * bounds.height;

        }

        int n = 0;

        for (boolean b : mask) {

            if (b) {

                n++;

            }

        }

        return n;

    }

}


