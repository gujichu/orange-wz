package orange.wz.gui.component.imageeditor;

import lombok.Getter;
import lombok.Setter;
import orange.wz.model.TransferableImage;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

@Getter
public class ImageEditorCanvas extends JPanel {

    private static final int CHECKER_SIZE = 8;

    private enum FloatState { NONE, COPY_PREVIEW, MOVING }

    private final ImageEditorLayerStack layerStack = new ImageEditorLayerStack();
    private ImageEditorSelection selection;
    private ImageEditorTool currentTool = ImageEditorTool.SELECT;
    @Setter
    private Color foregroundColor = Color.BLACK;
    @Setter
    private int brushSize = 4;
    @Setter
    private int fillTolerance = 32;
    @Setter
    private int magicWandTolerance = 32;
    private double zoom = 1.0;
    private int viewOffsetX;
    private int viewOffsetY;

    private Point dragStart;
    private Point lastPaintPoint;
    private Point panStartScreen;
    private int panStartOffsetX;
    private int panStartOffsetY;
    private boolean dragging;
    private boolean paintingStroke;

    private FloatState floatState = FloatState.NONE;
    private BufferedImage floatingImage;
    private ImageEditorSelection floatingMask;
    private Point floatOrigin;
    private Point floatDragAnchor;
    private boolean movingContent;
    private boolean movingLayer;
    private Point layerDragStartImg;
    private int layerDragStartOffsetX;
    private int layerDragStartOffsetY;

    private GeneralPath selectionOutlineCache;
    private ImageEditorSelection selectionOverlaySource;
    private Timer marchingAntsTimer;
    private float marchPhase;
    private boolean selectionDragActive;

    private final ImageEditorHistory history = new ImageEditorHistory();
    private BufferedImage clipboardBuffer;

    @Setter
    private Consumer<Color> colorPickedListener;
    @Setter
    private Consumer<String> statusListener;
    @Setter
    private Runnable imageChangedListener;
    @Setter
    private Consumer<java.util.List<File>> filesDroppedListener;
    @Setter
    private Runnable historyChangedListener;
    @Setter
    private Runnable historyIndexChangedListener;
    @Setter
    private Consumer<Double> zoomChangedListener;
    @Setter
    private Runnable layersChangedListener;

    public BufferedImage getImage() {
        return layerStack.composite();
    }

    public BufferedImage getEditImage() {
        return layerStack.getActiveLayerImage();
    }

    private boolean hasImage() {
        return !layerStack.isEmpty();
    }

    private int canvasWidth() {
        return layerStack.getCanvasWidth();
    }

    private int canvasHeight() {
        return layerStack.getCanvasHeight();
    }

    private void notifyLayersChanged() {
        if (layersChangedListener != null) {
            layersChangedListener.run();
        }
    }

    void commitHistoryQuiet(String label) {
        commitHistory(label);
    }

    public ImageEditorCanvas() {
        setBackground(new Color(0x2B2B2B));
        setDoubleBuffered(true);
        setFocusable(true);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        new DropTarget(this, DnDConstants.ACTION_COPY, new ImageDropTarget(), true);

        history.setStructureChangedListener(() -> {
            if (historyChangedListener != null) {
                historyChangedListener.run();
            }
        });
        history.setIndexChangedListener(() -> {
            if (historyIndexChangedListener != null) {
                historyIndexChangedListener.run();
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!hasImage() || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (currentTool == ImageEditorTool.PAN) {
                    Point imgPt = screenToImage(e.getPoint());
                    if (tryStartFloatDrag(imgPt)) {
                        dragging = true;
                        movingContent = true;
                        floatDragAnchor = imgPt;
                        return;
                    }
                    if (hasExplicitSelection() && selection.contains(imgPt.x, imgPt.y)) {
                        startMoveSelection();
                        dragging = true;
                        movingContent = true;
                        floatDragAnchor = imgPt;
                        return;
                    }
                    if (layerStack.hasActiveLayer() && hitActiveLayerContent(imgPt.x, imgPt.y)) {
                        startMoveLayer(imgPt);
                        dragging = true;
                        movingLayer = true;
                        return;
                    }
                    dragging = true;
                    movingContent = false;
                    movingLayer = false;
                    panStartScreen = e.getPoint();
                    panStartOffsetX = viewOffsetX;
                    panStartOffsetY = viewOffsetY;
                    return;
                }
                Point imgPt = screenToImage(e.getPoint());
                dragStart = imgPt;
                lastPaintPoint = imgPt;
                dragging = true;
                handlePress(imgPt);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging || !hasImage()) {
                    return;
                }
                if (currentTool == ImageEditorTool.PAN) {
                    if (movingContent) {
                        finishFloatDrag();
                    }
                    if (movingLayer) {
                        commitHistory("移动图层");
                        notifyLayersChanged();
                        movingLayer = false;
                        layerDragStartImg = null;
                    }
                    dragging = false;
                    movingContent = false;
                    floatDragAnchor = null;
                    panStartScreen = null;
                    return;
                }
                if (paintingStroke) {
                    commitHistory("绘制");
                    paintingStroke = false;
                }
                dragging = false;
                handleRelease(screenToImage(e.getPoint()));
                dragStart = null;
                lastPaintPoint = null;
            }
        };
        addMouseListener(mouse);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging || !hasImage()) {
                    return;
                }
                if (currentTool == ImageEditorTool.PAN) {
                    if (movingContent && floatDragAnchor != null && floatingImage != null && floatOrigin != null) {
                        Point imgPt = screenToImage(e.getPoint());
                        int dx = imgPt.x - floatDragAnchor.x;
                        int dy = imgPt.y - floatDragAnchor.y;
                        floatOrigin = new Point(floatOrigin.x + dx, floatOrigin.y + dy);
                        floatDragAnchor = imgPt;
                        if (floatingMask != null) {
                            int tdx = floatOrigin.x - floatingMask.getBounds().x;
                            int tdy = floatOrigin.y - floatingMask.getBounds().y;
                            selection = ImageEditorSelection.translated(floatingMask, tdx, tdy);
                        }
                        repaint();
                        return;
                    }
                    if (movingLayer && layerDragStartImg != null) {
                        Point imgPt = screenToImage(e.getPoint());
                        ImageEditorLayer layer = layerStack.getActiveLayer();
                        if (layer != null) {
                            layer.setOffsetX(layerDragStartOffsetX + imgPt.x - layerDragStartImg.x);
                            layer.setOffsetY(layerDragStartOffsetY + imgPt.y - layerDragStartImg.y);
                            markImageDirty();
                            repaint();
                        }
                        return;
                    }
                    if (panStartScreen != null) {
                        viewOffsetX = panStartOffsetX + (e.getX() - panStartScreen.x);
                        viewOffsetY = panStartOffsetY + (e.getY() - panStartScreen.y);
                        repaint();
                    }
                    return;
                }
                Point imgPt = screenToImage(e.getPoint());
                handleDrag(imgPt);
                if (currentTool == ImageEditorTool.SELECT && dragStart != null) {
                    selectionDragActive = true;
                    selection = ImageEditorSelection.rectangle(normalizeRect(dragStart, imgPt),
                            canvasWidth(), canvasHeight());
                    invalidateSelectionOverlay();
                    repaintImageArea();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (!hasImage()) {
                    return;
                }
                Point p = screenToImage(e.getPoint());
                updateStatus(String.format("坐标 (%d, %d)  缩放 %.0f%%", p.x, p.y, zoom * 100));
            }
        });

        addMouseWheelListener(e -> {
            double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
            zoomAtPoint(e.getPoint(), factor);
            e.consume();
        });

        registerShortcuts();
    }

    private void registerShortcuts() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelection();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        am.put("paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commitFloat");
        am.put("commitFloat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commitFloating();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelFloat");
        am.put("cancelFloat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelFloating(false);
            }
        });
    }

    public void setImage(BufferedImage img, String historyLabel) {
        layerStack.initFromImage(img);
        this.selection = null;
        invalidateSelectionOverlay();
        clearFloating();
        history.clear();
        if (hasImage()) {
            history.push(historyLabel, getImage());
        }
        revalidate();
        repaint();
        notifyImageChanged();
        notifyLayersChanged();
        if (hasImage()) {
            updateStatus(String.format("已加载 %d × %d", canvasWidth(), canvasHeight()));
        }
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.05, Math.min(32.0, zoom));
        revalidate();
        repaint();
        notifyZoomChanged();
    }

    private void zoomAtPoint(Point screen, double factor) {
        if (!hasImage()) {
            return;
        }
        int[] origin = imageOriginOnScreen();
        int ox = origin[0];
        int oy = origin[1];
        int drawW = origin[2];
        int drawH = origin[3];

        if (screen.x < ox || screen.y < oy || screen.x >= ox + drawW || screen.y >= oy + drawH) {
            setZoom(zoom * factor);
            return;
        }

        double imgX = (screen.x - ox) / zoom;
        double imgY = (screen.y - oy) / zoom;
        double newZoom = Math.max(0.05, Math.min(32.0, zoom * factor));
        int newDrawW = (int) Math.ceil(canvasWidth() * newZoom);
        int newDrawH = (int) Math.ceil(canvasHeight() * newZoom);

        int newOx = screen.x - (int) Math.round(imgX * newZoom);
        int newOy = screen.y - (int) Math.round(imgY * newZoom);
        viewOffsetX = newOx - (getWidth() - newDrawW) / 2;
        viewOffsetY = newOy - (getHeight() - newDrawH) / 2;
        zoom = newZoom;
        revalidate();
        repaint();
        notifyZoomChanged();
    }

    private int[] imageOriginOnScreen() {
        int drawW = (int) Math.ceil(canvasWidth() * zoom);
        int drawH = (int) Math.ceil(canvasHeight() * zoom);
        int ox = (getWidth() - drawW) / 2 + viewOffsetX;
        int oy = (getHeight() - drawH) / 2 + viewOffsetY;
        return new int[]{ox, oy, drawW, drawH};
    }

    private void notifyZoomChanged() {
        if (zoomChangedListener != null) {
            zoomChangedListener.accept(zoom);
        }
    }

    public void setCurrentTool(ImageEditorTool tool) {
        if (tool != ImageEditorTool.PAN && floatState == FloatState.COPY_PREVIEW) {
            commitFloating();
        }
        this.currentTool = tool;
        setCursor(switch (tool) {
            case PAN -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            case BUCKET -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            default -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        });
    }

    public void clearSelection() {
        selection = null;
        invalidateSelectionOverlay();
        cancelFloating(false);
        repaint();
    }

    public void setSelection(ImageEditorSelection sel) {
        this.selection = sel;
        invalidateSelectionOverlay();
        repaint();
    }

    private void invalidateSelectionOverlay() {
        selectionOutlineCache = null;
        selectionOverlaySource = null;
        syncMarchingAntsTimer();
    }

    private void syncMarchingAntsTimer() {
        boolean active = !selectionDragActive && !movingContent && !movingLayer
                && (hasSelectionOutline(selection)
                || (floatState != FloatState.NONE && hasSelectionOutline(floatingMask)));
        if (active) {
            if (marchingAntsTimer == null) {
                marchingAntsTimer = new Timer(120, e -> {
                    marchPhase = (marchPhase + 1f) % 8f;
                    repaintActiveSelectionAreas();
                });
                marchingAntsTimer.setCoalesce(true);
                marchingAntsTimer.start();
            }
        } else if (marchingAntsTimer != null) {
            marchingAntsTimer.stop();
            marchingAntsTimer = null;
            marchPhase = 0f;
        }
    }

    private static boolean hasSelectionOutline(ImageEditorSelection sel) {
        return sel != null && !sel.isEmpty();
    }

    private void markImageDirty() {
        layerStack.markCompositeDirty();
    }

    public ImageEditorSelection getEffectiveSelection() {
        if (!hasImage()) {
            return null;
        }
        if (selection == null || selection.isEmpty()) {
            return ImageEditorSelection.rectangle(new Rectangle(0, 0, canvasWidth(), canvasHeight()),
                    canvasWidth(), canvasHeight());
        }
        return selection;
    }

    public boolean hasExplicitSelection() {
        return selection != null && !selection.isEmpty();
    }

    public void commitHistory(String label) {
        if (hasImage()) {
            markImageDirty();
            history.push(label, getImage());
            notifyImageChanged();
        }
    }

    public void applyAndRepaint(String label, Runnable action) {
        if (!hasImage()) {
            return;
        }
        markImageDirty();
        action.run();
        commitHistory(label);
        repaint();
    }

    void disposeResources() {
        if (marchingAntsTimer != null) {
            marchingAntsTimer.stop();
            marchingAntsTimer = null;
        }
        history.clear();
        layerStack.initFromImage(null);
        clearFloating();
        selection = null;
        invalidateSelectionOverlay();
        clipboardBuffer = null;
        colorPickedListener = null;
        statusListener = null;
        imageChangedListener = null;
        filesDroppedListener = null;
        historyChangedListener = null;
        historyIndexChangedListener = null;
        zoomChangedListener = null;
        layersChangedListener = null;
    }

    public void stepBack() {
        BufferedImage snap = history.stepBack();
        if (snap != null) {
            layerStack.restoreFromFlat(ImageEditorUtil.deepCopy(snap));
            clearFloating();
            selection = null;
            invalidateSelectionOverlay();
            repaint();
            notifyImageChanged();
            notifyLayersChanged();
            updateStatus("上一步");
        }
    }

    public void stepForward() {
        BufferedImage snap = history.stepForward();
        if (snap != null) {
            layerStack.restoreFromFlat(ImageEditorUtil.deepCopy(snap));
            clearFloating();
            selection = null;
            invalidateSelectionOverlay();
            repaint();
            notifyImageChanged();
            notifyLayersChanged();
            updateStatus("下一步");
        }
    }

    public void goToHistory(int index) {
        BufferedImage snap = history.goTo(index);
        if (snap != null) {
            layerStack.restoreFromFlat(ImageEditorUtil.deepCopy(snap));
            clearFloating();
            selection = null;
            invalidateSelectionOverlay();
            repaint();
            notifyImageChanged();
            notifyLayersChanged();
            updateStatus("已跳转到历史记录");
        }
    }

    void replaceImage(BufferedImage img) {
        layerStack.initFromImage(img);
        revalidate();
        repaint();
        notifyLayersChanged();
    }

    public void copySelection() {
        if (!hasImage() || !hasExplicitSelection()) {
            updateStatus("请先建立选区");
            return;
        }
        floatingImage = ImageEditorUtil.extractSelection(getImage(), selection);
        floatingMask = selection;
        floatOrigin = selection.getBounds().getLocation();
        floatState = FloatState.COPY_PREVIEW;
        clipboardBuffer = floatingImage;
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new TransferableImage(floatingImage), null);
        setCurrentTool(ImageEditorTool.PAN);
        updateStatus("已复制，粘贴将创建新图层");
        repaint();
    }

    public void pasteFromClipboard() {
        if (!hasImage()) {
            return;
        }
        BufferedImage clip = readClipboardImage();
        if (clip == null) {
            clip = clipboardBuffer;
        }
        if (clip == null) {
            updateStatus("剪贴板中没有图片");
            return;
        }
        cancelFloating(false);
        int w = canvasWidth();
        int h = canvasHeight();
        int x = Math.max(0, (w - clip.getWidth()) / 2);
        int y = Math.max(0, (h - clip.getHeight()) / 2);
        layerStack.addTopLayerFromPartial(clip, x, y, null);
        selection = ImageEditorSelection.rectangle(
                new Rectangle(x, y, clip.getWidth(), clip.getHeight()), w, h);
        commitHistory("粘贴");
        notifyLayersChanged();
        setCurrentTool(ImageEditorTool.PAN);
        updateStatus("已粘贴到新图层，可拖动调整位置");
        repaint();
    }

    private void startMoveSelection() {
        if (!hasExplicitSelection() || !layerStack.hasActiveLayer()) {
            return;
        }
        ImageEditorLayer layer = layerStack.getActiveLayer();
        ImageEditorSelection layerSel = toLayerSelection(selection);
        BufferedImage edit = getEditImage();
        floatingImage = ImageEditorUtil.extractSelection(edit, layerSel);
        floatingMask = selection;
        floatOrigin = selection.getBounds().getLocation();
        ImageEditorUtil.clearSelectionPixels(edit, layerSel);
        floatState = FloatState.MOVING;
        markImageDirty();
        updateStatus("拖动移动选区");
    }

    private void startMoveLayer(Point imgPt) {
        ImageEditorLayer layer = layerStack.getActiveLayer();
        if (layer == null) {
            return;
        }
        layerDragStartImg = new Point(imgPt);
        layerDragStartOffsetX = layer.getOffsetX();
        layerDragStartOffsetY = layer.getOffsetY();
        updateStatus("拖动移动图层");
    }

    private boolean hitActiveLayerContent(int canvasX, int canvasY) {
        ImageEditorLayer layer = layerStack.getActiveLayer();
        if (layer == null || !layer.isVisible()) {
            return false;
        }
        int lx = canvasX - layer.getOffsetX();
        int ly = canvasY - layer.getOffsetY();
        BufferedImage img = layer.getContent();
        if (lx < 0 || ly < 0 || lx >= img.getWidth() || ly >= img.getHeight()) {
            return false;
        }
        return ((img.getRGB(lx, ly) >> 24) & 0xFF) > 0;
    }

    private Point toLayerPoint(Point canvasPt) {
        ImageEditorLayer layer = layerStack.getActiveLayer();
        if (layer == null) {
            return canvasPt;
        }
        return new Point(canvasPt.x - layer.getOffsetX(), canvasPt.y - layer.getOffsetY());
    }

    private ImageEditorSelection toLayerSelection(ImageEditorSelection canvasSel) {
        ImageEditorLayer layer = layerStack.getActiveLayer();
        if (canvasSel == null || layer == null) {
            return canvasSel;
        }
        return ImageEditorSelection.translated(canvasSel, -layer.getOffsetX(), -layer.getOffsetY());
    }

    private boolean isInActiveLayerImage(Point layerPt) {
        BufferedImage edit = getEditImage();
        if (edit == null) {
            return false;
        }
        return layerPt.x >= 0 && layerPt.y >= 0 && layerPt.x < edit.getWidth() && layerPt.y < edit.getHeight();
    }

    private void compositeFloatingToActiveLayer(BufferedImage edit, BufferedImage floating, Point canvasOrigin,
                                                ImageEditorSelection canvasMask) {
        ImageEditorLayer layer = layerStack.getActiveLayer();
        if (layer == null) {
            return;
        }
        int destX = canvasOrigin.x - layer.getOffsetX();
        int destY = canvasOrigin.y - layer.getOffsetY();
        ImageEditorUtil.compositeFloating(edit, floating, destX, destY, toLayerSelection(canvasMask));
    }

    private boolean tryStartFloatDrag(Point imgPt) {
        if (floatingImage == null || floatOrigin == null || floatingMask == null) {
            return false;
        }
        int lx = imgPt.x - floatOrigin.x;
        int ly = imgPt.y - floatOrigin.y;
        if (lx < 0 || ly < 0 || lx >= floatingImage.getWidth() || ly >= floatingImage.getHeight()) {
            return false;
        }
        Rectangle b = floatingMask.getBounds();
        return floatingMask.contains(b.x + lx, b.y + ly);
    }

    private void finishFloatDrag() {
        if (floatState == FloatState.MOVING && floatingImage != null && floatOrigin != null && floatingMask != null) {
            BufferedImage edit = getEditImage();
            compositeFloatingToActiveLayer(edit, floatingImage, floatOrigin, floatingMask);
            int dx = floatOrigin.x - floatingMask.getBounds().x;
            int dy = floatOrigin.y - floatingMask.getBounds().y;
            selection = ImageEditorSelection.translated(floatingMask, dx, dy);
            commitHistory("移动选区");
            clearFloating();
            repaint();
            updateStatus("选区已移动");
        } else if (floatState == FloatState.COPY_PREVIEW && floatingImage != null && floatOrigin != null) {
            int dx = floatOrigin.x - (floatingMask != null ? floatingMask.getBounds().x : floatOrigin.x);
            int dy = floatOrigin.y - (floatingMask != null ? floatingMask.getBounds().y : floatOrigin.y);
            if (floatingMask != null) {
                selection = ImageEditorSelection.translated(floatingMask, dx, dy);
            }
            repaint();
        }
    }

    public void commitFloating() {
        if (floatState == FloatState.NONE || floatingImage == null || floatOrigin == null) {
            return;
        }
        if (floatState == FloatState.COPY_PREVIEW) {
            layerStack.addTopLayerFromPartial(floatingImage, floatOrigin.x, floatOrigin.y, null);
            if (floatingMask != null) {
                int dx = floatOrigin.x - floatingMask.getBounds().x;
                int dy = floatOrigin.y - floatingMask.getBounds().y;
                selection = ImageEditorSelection.translated(floatingMask, dx, dy);
            }
            commitHistory("粘贴");
            notifyLayersChanged();
        } else if (floatingMask != null) {
            BufferedImage edit = getEditImage();
            compositeFloatingToActiveLayer(edit, floatingImage, floatOrigin, floatingMask);
            int dx = floatOrigin.x - floatingMask.getBounds().x;
            int dy = floatOrigin.y - floatingMask.getBounds().y;
            selection = ImageEditorSelection.translated(floatingMask, dx, dy);
            commitHistory("移动选区");
        }
        clearFloating();
        repaint();
        updateStatus("已放置");
    }

    private void cancelFloating(boolean restoreCut) {
        if (floatState == FloatState.NONE) {
            return;
        }
        if (restoreCut && floatState == FloatState.MOVING && floatingImage != null && floatingMask != null) {
            compositeFloatingToActiveLayer(getEditImage(), floatingImage,
                    floatingMask.getBounds().getLocation(), floatingMask);
        }
        clearFloating();
        repaint();
    }

    private void clearFloating() {
        floatState = FloatState.NONE;
        floatingImage = null;
        floatingMask = null;
        floatOrigin = null;
        floatDragAnchor = null;
    }

    private BufferedImage readClipboardImage() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return ImageEditorUtil.toArgb((BufferedImage) cb.getData(DataFlavor.imageFlavor));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        if (!hasImage()) {
            return new Dimension(640, 480);
        }
        return new Dimension(
                Math.max(1, (int) Math.ceil(canvasWidth() * zoom) + Math.abs(viewOffsetX) * 2),
                Math.max(1, (int) Math.ceil(canvasHeight() * zoom) + Math.abs(viewOffsetY) * 2)
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (!hasImage()) {
            g2.setColor(Color.GRAY);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
            String hint = "拖放图片到此处，或使用「打开」加载";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, getHeight() / 2);
            g2.dispose();
            return;
        }

        int[] origin = imageOriginOnScreen();
        int ox = origin[0];
        int oy = origin[1];
        int drawW = origin[2];
        int drawH = origin[3];

        drawCheckerboard(g2, ox, oy, drawW, drawH);
        BufferedImage composite = getImage();
        if (composite != null) {
            g2.drawImage(composite, ox, oy, drawW, drawH, null);
        }

        if (floatState != FloatState.NONE && floatingImage != null && floatOrigin != null) {
            int fx = ox + (int) Math.round(floatOrigin.x * zoom);
            int fy = oy + (int) Math.round(floatOrigin.y * zoom);
            int fw = (int) Math.ceil(floatingImage.getWidth() * zoom);
            int fh = (int) Math.ceil(floatingImage.getHeight() * zoom);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.drawImage(floatingImage, fx, fy, fw, fh, null);
        }

        drawSelectionOverlay(g2, ox, oy, selection);
        if (floatState != FloatState.NONE && floatingMask != null && selection != floatingMask) {
            drawSelectionOverlay(g2, ox, oy, floatingMask);
        }
        g2.dispose();
    }

    private void drawSelectionOverlay(Graphics2D g2, int ox, int oy, ImageEditorSelection sel) {
        if (sel == null || sel.isEmpty() || !hasImage()) {
            return;
        }
        float phase = selectionDragActive || movingContent ? 0f : marchPhase;
        if (sel.isRectangular()) {
            Rectangle b = sel.getBounds();
            int sx = ox + (int) Math.round(b.x * zoom);
            int sy = oy + (int) Math.round(b.y * zoom);
            int sw = Math.max(1, (int) Math.round(b.width * zoom));
            int sh = Math.max(1, (int) Math.round(b.height * zoom));
            drawMarchingAntsShape(g2, new Rectangle2D.Float(sx, sy, sw, sh), phase);
        } else {
            drawMarchingAntsPath(g2, ox, oy, sel, phase);
        }
    }

    private void drawMarchingAntsPath(Graphics2D g2, int ox, int oy, ImageEditorSelection sel, float phase) {
        GeneralPath outline = getSelectionOutline(sel);
        if (outline.getCurrentPoint() == null) {
            return;
        }
        AffineTransform tx = new AffineTransform();
        tx.translate(ox, oy);
        tx.scale(zoom, zoom);
        drawMarchingAntsShape(g2, tx.createTransformedShape(outline), phase);
    }

    private void drawMarchingAntsShape(Graphics2D g2, Shape shape, float phase) {
        float dash = 4f;
        float[] pattern = {dash, dash};
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, pattern, phase));
        g2.setColor(Color.BLACK);
        g2.draw(shape);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, pattern, phase + dash));
        g2.draw(shape);
    }

    private Rectangle getSelectionScreenBounds(ImageEditorSelection sel) {
        if (sel == null || sel.isEmpty() || !hasImage()) {
            return null;
        }
        Rectangle b = sel.getBounds();
        int[] origin = imageOriginOnScreen();
        int pad = 8;
        int x = origin[0] + (int) Math.floor(b.x * zoom) - pad;
        int y = origin[1] + (int) Math.floor(b.y * zoom) - pad;
        int w = Math.max(1, (int) Math.ceil(b.width * zoom) + pad * 2);
        int h = Math.max(1, (int) Math.ceil(b.height * zoom) + pad * 2);
        return new Rectangle(x, y, w, h);
    }

    private void repaintActiveSelectionAreas() {
        Rectangle dirty = null;
        if (hasSelectionOutline(selection)) {
            dirty = getSelectionScreenBounds(selection);
        }
        if (floatState != FloatState.NONE && hasSelectionOutline(floatingMask)) {
            Rectangle fm = getSelectionScreenBounds(floatingMask);
            dirty = dirty == null ? fm : dirty.union(fm);
        }
        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    private void repaintImageArea() {
        if (!hasImage()) {
            repaint();
            return;
        }
        int[] origin = imageOriginOnScreen();
        int pad = 8;
        repaint(origin[0] - pad, origin[1] - pad, origin[2] + pad * 2, origin[3] + pad * 2);
    }

    private void repaintSelectionArea(ImageEditorSelection sel) {
        Rectangle dirty = getSelectionScreenBounds(sel);
        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        } else {
            repaint();
        }
    }

    private GeneralPath getSelectionOutline(ImageEditorSelection sel) {
        if (sel == selectionOverlaySource && selectionOutlineCache != null) {
            return selectionOutlineCache;
        }
        selectionOutlineCache = ImageEditorUtil.buildSelectionOutline(sel);
        selectionOverlaySource = sel;
        return selectionOutlineCache;
    }

    private void handlePress(Point p) {
        BufferedImage composite = getImage();
        BufferedImage edit = getEditImage();
        switch (currentTool) {
            case EYEDROPPER -> {
                Color c = ImageEditorUtil.pickColor(composite, p.x, p.y);
                foregroundColor = c;
                if (colorPickedListener != null) {
                    colorPickedListener.accept(c);
                }
                updateStatus(String.format("拾取 #%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
            }
            case MAGIC_WAND -> {
                selectionDragActive = false;
                selection = ImageEditorUtil.magicWandSelect(composite, p.x, p.y, magicWandTolerance);
                invalidateSelectionOverlay();
                repaintSelectionArea(selection);
                if (selection != null) {
                    Rectangle b = selection.getBounds();
                    updateStatus(String.format("魔棒选区 %d 像素 (%d×%d)", selection.countSelectedPixels(), b.width, b.height));
                }
            }
            case BUCKET -> {
                if (edit == null || !layerStack.hasActiveLayer()) {
                    updateStatus("请先选择图层");
                    return;
                }
                Point lp = toLayerPoint(p);
                if (!isInActiveLayerImage(lp)) {
                    return;
                }
                ImageEditorSelection limit = hasExplicitSelection() ? toLayerSelection(selection) : null;
                ImageEditorUtil.floodFill(edit, lp.x, lp.y, foregroundColor, fillTolerance, limit);
                markImageDirty();
                commitHistory("油漆桶填充");
                repaint();
            }
            case BRUSH, ERASER -> {
                if (edit == null || !layerStack.hasActiveLayer()) {
                    updateStatus("请先选择图层");
                    return;
                }
                Point lp = toLayerPoint(p);
                if (!isInActiveLayerImage(lp)) {
                    return;
                }
                paintingStroke = true;
                boolean erase = currentTool == ImageEditorTool.ERASER;
                markImageDirty();
                ImageEditorUtil.drawBrush(edit, lp.x, lp.y, brushSize, foregroundColor, erase,
                        hasExplicitSelection() ? toLayerSelection(selection) : null);
                repaint();
            }
            default -> {
            }
        }
    }

    private void handleDrag(Point p) {
        if (currentTool == ImageEditorTool.BRUSH || currentTool == ImageEditorTool.ERASER) {
            if (lastPaintPoint != null) {
                drawLine(lastPaintPoint, p, currentTool == ImageEditorTool.ERASER);
            }
            lastPaintPoint = p;
            repaint();
        }
    }

    private void handleRelease(Point p) {
        if (currentTool == ImageEditorTool.SELECT && dragStart != null) {
            selection = ImageEditorSelection.rectangle(normalizeRect(dragStart, p), canvasWidth(), canvasHeight());
            if (selection != null && selection.getBounds().width < 2 && selection.getBounds().height < 2) {
                Rectangle b = selection.getBounds();
                selection = ImageEditorSelection.rectangle(new Rectangle(b.x, b.y, 1, 1),
                        canvasWidth(), canvasHeight());
            }
            selectionDragActive = false;
            invalidateSelectionOverlay();
            repaintSelectionArea(selection);
            if (selection != null) {
                Rectangle b = selection.getBounds();
                updateStatus(String.format("选区 %d×%d @ (%d,%d)", b.width, b.height, b.x, b.y));
            }
        }
    }

    private void drawLine(Point from, Point to, boolean erase) {
        BufferedImage edit = getEditImage();
        if (edit == null || !layerStack.hasActiveLayer()) {
            return;
        }
        Point layerFrom = toLayerPoint(from);
        Point layerTo = toLayerPoint(to);
        ImageEditorSelection limit = hasExplicitSelection() ? toLayerSelection(selection) : null;
        markImageDirty();
        int dx = Math.abs(layerTo.x - layerFrom.x);
        int dy = Math.abs(layerTo.y - layerFrom.y);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            ImageEditorUtil.drawBrush(edit, layerTo.x, layerTo.y, brushSize, foregroundColor, erase, limit);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = layerFrom.x + (layerTo.x - layerFrom.x) * i / steps;
            int y = layerFrom.y + (layerTo.y - layerFrom.y) * i / steps;
            ImageEditorUtil.drawBrush(edit, x, y, brushSize, foregroundColor, erase, limit);
        }
    }

    private Point screenToImage(Point screen) {
        if (!hasImage()) {
            return new Point(0, 0);
        }
        int[] origin = imageOriginOnScreen();
        int x = (int) Math.floor((screen.x - origin[0]) / zoom);
        int y = (int) Math.floor((screen.y - origin[1]) / zoom);
        x = Math.max(0, Math.min(canvasWidth() - 1, x));
        y = Math.max(0, Math.min(canvasHeight() - 1, y));
        return new Point(x, y);
    }

    private static Rectangle normalizeRect(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private void drawCheckerboard(Graphics2D g2, int x, int y, int w, int h) {
        for (int cy = 0; cy < h; cy += CHECKER_SIZE) {
            for (int cx = 0; cx < w; cx += CHECKER_SIZE) {
                boolean light = ((cx / CHECKER_SIZE) + (cy / CHECKER_SIZE)) % 2 == 0;
                g2.setColor(light ? new Color(0xCCCCCC) : new Color(0x999999));
                g2.fillRect(x + cx, y + cy, Math.min(CHECKER_SIZE, w - cx), Math.min(CHECKER_SIZE, h - cy));
            }
        }
    }

    private void updateStatus(String text) {
        if (statusListener != null) {
            statusListener.accept(text);
        }
    }

    private void notifyImageChanged() {
        if (imageChangedListener != null) {
            imageChangedListener.run();
        }
    }

    void loadFile(File file, Consumer<File> onLoaded) {
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return javax.imageio.ImageIO.read(file);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        setImage(img, "打开");
                        updateStatus("已打开: " + file.getName());
                        onLoaded.accept(file);
                    } else {
                        updateStatus("无法读取: " + file.getName());
                    }
                } catch (Exception ex) {
                    updateStatus("打开失败: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private class ImageDropTarget extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                var transferable = dtde.getTransferable();
                if (transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    java.util.List<File> files = (java.util.List<File>) transferable.getTransferData(
                            java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        if (files.size() > 1 && filesDroppedListener != null) {
                            filesDroppedListener.accept(files);
                        } else {
                            loadFile(files.getFirst(), f -> {
                            });
                        }
                    }
                }
                dtde.dropComplete(true);
            } catch (Exception ex) {
                dtde.dropComplete(false);
                updateStatus("拖放失败: " + ex.getMessage());
            }
        }
    }
}
