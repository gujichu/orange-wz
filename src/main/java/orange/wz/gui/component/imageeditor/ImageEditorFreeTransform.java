package orange.wz.gui.component.imageeditor;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/** Photoshop 风格自由变换会话：8 控制点缩放 */
final class ImageEditorFreeTransform {

    static final int HANDLE_NW = 0;
    static final int HANDLE_N = 1;
    static final int HANDLE_NE = 2;
    static final int HANDLE_W = 3;
    static final int HANDLE_E = 4;
    static final int HANDLE_SW = 5;
    static final int HANDLE_S = 6;
    static final int HANDLE_SE = 7;

    private static final int MIN_SIZE = 2;
    private static final int HIT_RADIUS = 6;

    private final BufferedImage source;
    private final ImageEditorSelection layerMask;
    private final ImageEditorSelection canvasMask;
    private final Rectangle sourceBoundsCanvas;
    private final Rectangle sourceBoundsLayer;
    private final int layerOffsetX;
    private final int layerOffsetY;
    private final int canvasWidth;
    private final int canvasHeight;

    private Rectangle currentBoundsCanvas;
    private int activeHandle = -1;

    private ImageEditorFreeTransform(BufferedImage source, ImageEditorSelection layerMask,
                                     ImageEditorSelection canvasMask, Rectangle sourceBoundsCanvas,
                                     Rectangle sourceBoundsLayer, int layerOffsetX, int layerOffsetY,
                                     int canvasWidth, int canvasHeight) {
        this.source = source;
        this.layerMask = layerMask;
        this.canvasMask = canvasMask;
        this.sourceBoundsCanvas = new Rectangle(sourceBoundsCanvas);
        this.sourceBoundsLayer = new Rectangle(sourceBoundsLayer);
        this.layerOffsetX = layerOffsetX;
        this.layerOffsetY = layerOffsetY;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.currentBoundsCanvas = new Rectangle(sourceBoundsCanvas);
    }

    static ImageEditorFreeTransform begin(BufferedImage layer, ImageEditorSelection layerMask,
                                          ImageEditorSelection canvasMask, int layerOffsetX, int layerOffsetY,
                                          int canvasWidth, int canvasHeight) {
        Rectangle layerBounds = layerMask.getBounds();
        BufferedImage source = ImageEditorUtil.extractMaskedSubimagePublic(layer, layerMask);
        Rectangle canvasBounds = canvasMask.getBounds();
        return new ImageEditorFreeTransform(source, layerMask, canvasMask, canvasBounds, layerBounds,
                layerOffsetX, layerOffsetY, canvasWidth, canvasHeight);
    }

    void restoreSource(BufferedImage layer) {
        Rectangle b = layerMask.getBounds();
        ImageEditorUtil.compositeFloating(layer, source, b.x, b.y, layerMask);
    }

    Rectangle getCurrentBoundsCanvas() {
        return new Rectangle(currentBoundsCanvas);
    }

    BufferedImage renderPreview() {
        return ImageEditorUtil.resizeImage(source, currentBoundsCanvas.width, currentBoundsCanvas.height);
    }

    int hitTestHandle(Point screenPt, int originX, int originY, double zoom) {
        for (int i = 0; i < 8; i++) {
            Point hp = handleScreenPoint(i, originX, originY, zoom);
            if (hp.distance(screenPt) <= HIT_RADIUS + 2) {
                return i;
            }
        }
        return -1;
    }

    void beginHandleDrag(int handle) {
        activeHandle = handle;
    }

    void endHandleDrag() {
        activeHandle = -1;
    }

    void updateFromDrag(Point dragCanvas, boolean keepAspect) {
        if (activeHandle < 0) {
            return;
        }
        currentBoundsCanvas = computeBounds(activeHandle, sourceBoundsCanvas, dragCanvas, keepAspect);
    }

    ImageEditorSelection apply(BufferedImage layer) {
        BufferedImage scaled = ImageEditorUtil.resizeImage(source, currentBoundsCanvas.width, currentBoundsCanvas.height);
        Rectangle destLayer = canvasToLayerRect(currentBoundsCanvas);
        ImageEditorSelection scaledLayerMask = scaleSelection(
                layerMask, sourceBoundsLayer, destLayer, layer.getWidth(), layer.getHeight());
        ImageEditorUtil.compositeFloating(layer, scaled, destLayer.x, destLayer.y, scaledLayerMask);
        return scaleSelection(
                canvasMask, sourceBoundsCanvas, currentBoundsCanvas, canvasWidth, canvasHeight);
    }

    void drawOverlay(Graphics2D g2, int ox, int oy, double zoom) {
        Rectangle b = currentBoundsCanvas;
        int sx = ox + (int) Math.round(b.x * zoom);
        int sy = oy + (int) Math.round(b.y * zoom);
        int sw = Math.max(1, (int) Math.round(b.width * zoom));
        int sh = Math.max(1, (int) Math.round(b.height * zoom));

        g2.setColor(new Color(0, 120, 215));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(sx, sy, sw, sh);

        int hs = Math.max(5, (int) Math.round(5 * zoom));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 8; i++) {
            Point p = handleScreenPoint(i, ox, oy, zoom);
            g2.fillRect(p.x - hs / 2, p.y - hs / 2, hs, hs);
            g2.setColor(Color.BLACK);
            g2.drawRect(p.x - hs / 2, p.y - hs / 2, hs, hs);
            g2.setColor(Color.WHITE);
        }
    }

    private Point handleScreenPoint(int handle, int ox, int oy, double zoom) {
        Point2D.Double cp = handleCanvasPoint(handle, currentBoundsCanvas);
        return new Point(
                ox + (int) Math.round(cp.x * zoom),
                oy + (int) Math.round(cp.y * zoom));
    }

    static Point2D.Double handleCanvasPoint(int handle, Rectangle r) {
        double x = r.x;
        double y = r.y;
        double w = r.width;
        double h = r.height;
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double right = x + w;
        double bottom = y + h;
        return switch (handle) {
            case HANDLE_NW -> new Point2D.Double(x, y);
            case HANDLE_N -> new Point2D.Double(cx, y);
            case HANDLE_NE -> new Point2D.Double(right, y);
            case HANDLE_W -> new Point2D.Double(x, cy);
            case HANDLE_E -> new Point2D.Double(right, cy);
            case HANDLE_SW -> new Point2D.Double(x, bottom);
            case HANDLE_S -> new Point2D.Double(cx, bottom);
            default -> new Point2D.Double(right, bottom);
        };
    }

    private Rectangle canvasToLayerRect(Rectangle canvasRect) {
        return new Rectangle(
                canvasRect.x - layerOffsetX,
                canvasRect.y - layerOffsetY,
                canvasRect.width,
                canvasRect.height);
    }

    private static Rectangle computeBounds(int handle, Rectangle origin, Point drag, boolean keepAspect) {
        int ox = origin.x;
        int oy = origin.y;
        int ow = origin.width;
        int oh = origin.height;
        int right = ox + ow;
        int bottom = oy + oh;
        int cx = ox + ow / 2;
        int cy = oy + oh / 2;

        int nx = ox;
        int ny = oy;
        int nw = ow;
        int nh = oh;

        switch (handle) {
            case HANDLE_NW -> {
                nx = drag.x;
                ny = drag.y;
                nw = right - nx;
                nh = bottom - ny;
            }
            case HANDLE_N -> {
                ny = drag.y;
                nh = bottom - ny;
            }
            case HANDLE_NE -> {
                ny = drag.y;
                nw = drag.x - ox;
                nh = bottom - ny;
            }
            case HANDLE_W -> {
                nx = drag.x;
                nw = right - nx;
            }
            case HANDLE_E -> nw = drag.x - ox;
            case HANDLE_SW -> {
                nx = drag.x;
                nw = right - nx;
                nh = drag.y - oy;
            }
            case HANDLE_S -> nh = drag.y - oy;
            case HANDLE_SE -> {
                nw = drag.x - ox;
                nh = drag.y - oy;
            }
            default -> {
            }
        }

        if (keepAspect && ow > 0 && oh > 0) {
            double aspect = (double) ow / oh;
            if (handle == HANDLE_N || handle == HANDLE_S) {
                nw = (int) Math.round(nh * aspect);
            } else if (handle == HANDLE_W || handle == HANDLE_E) {
                nh = (int) Math.round(nw / aspect);
            } else {
                double scale = Math.max(Math.abs((double) nw / ow), Math.abs((double) nh / oh));
                nw = (int) Math.round(ow * scale * Math.signum(nw));
                nh = (int) Math.round(oh * scale * Math.signum(nh));
            }
            switch (handle) {
                case HANDLE_NW -> {
                    nx = right - nw;
                    ny = bottom - nh;
                }
                case HANDLE_N -> {
                    nx = cx - nw / 2;
                }
                case HANDLE_NE -> {
                    ny = bottom - nh;
                }
                case HANDLE_W -> {
                    nx = right - nw;
                    ny = cy - nh / 2;
                }
                case HANDLE_E -> ny = cy - nh / 2;
                case HANDLE_SW -> nx = right - nw;
                case HANDLE_S -> nx = cx - nw / 2;
                default -> {
                }
            }
        }

        return normalizeSize(nx, ny, nw, nh);
    }

    private static Rectangle normalizeSize(int x, int y, int w, int h) {
        if (w < 0) {
            x += w;
            w = -w;
        }
        if (h < 0) {
            y += h;
            h = -h;
        }
        w = Math.max(MIN_SIZE, w);
        h = Math.max(MIN_SIZE, h);
        return new Rectangle(x, y, w, h);
    }

    static ImageEditorSelection scaleSelection(ImageEditorSelection sel, Rectangle from, Rectangle to,
                                               int imgW, int imgH) {
        if (sel == null || sel.isEmpty() || from.width <= 0 || from.height <= 0) {
            return sel;
        }
        if (sel.isRectangular() && from.equals(sel.getBounds())) {
            return ImageEditorSelection.rectangle(to, imgW, imgH);
        }
        double sx = (double) to.width / from.width;
        double sy = (double) to.height / from.height;
        boolean[] mask = new boolean[imgW * imgH];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        Rectangle b = sel.getBounds();
        for (int y = b.y; y < b.y + b.height; y++) {
            for (int x = b.x; x < b.x + b.width; x++) {
                if (!sel.contains(x, y)) {
                    continue;
                }
                int nx = to.x + (int) Math.round((x - from.x) * sx);
                int ny = to.y + (int) Math.round((y - from.y) * sy);
                if (nx >= 0 && ny >= 0 && nx < imgW && ny < imgH) {
                    mask[ny * imgW + nx] = true;
                    minX = Math.min(minX, nx);
                    minY = Math.min(minY, ny);
                    maxX = Math.max(maxX, nx);
                    maxY = Math.max(maxY, ny);
                }
            }
        }
        if (minX > maxX) {
            return ImageEditorSelection.rectangle(to, imgW, imgH);
        }
        return ImageEditorSelection.fromMask(imgW, imgH, mask,
                new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
    }
}
