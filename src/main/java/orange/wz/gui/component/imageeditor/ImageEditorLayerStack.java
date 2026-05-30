package orange.wz.gui.component.imageeditor;

import lombok.Getter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public final class ImageEditorLayerStack {

    private final List<ImageEditorLayer> layers = new ArrayList<>();
    private int activeIndex = -1;
    private int canvasWidth;
    private int canvasHeight;
    private int nameSeq = 1;
    private BufferedImage compositeCache;
    private boolean compositeDirty = true;

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public void markCompositeDirty() {
        compositeDirty = true;
    }

    public void initFromImage(BufferedImage img) {
        layers.clear();
        compositeDirty = true;
        compositeCache = null;
        if (img == null) {
            canvasWidth = 0;
            canvasHeight = 0;
            activeIndex = -1;
            return;
        }
        canvasWidth = img.getWidth();
        canvasHeight = img.getHeight();
        layers.add(new ImageEditorLayer("背景", ImageEditorUtil.deepCopy(img), true));
        activeIndex = 0;
        nameSeq = 2;
    }

    public void restoreFromFlat(BufferedImage flat) {
        initFromImage(flat);
    }

    public BufferedImage composite() {
        if (layers.isEmpty()) {
            return null;
        }
        if (!compositeDirty && compositeCache != null) {
            return compositeCache;
        }
        BufferedImage out = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (ImageEditorLayer layer : layers) {
            if (layer.isVisible()) {
                g.drawImage(layer.getContent(), layer.getOffsetX(), layer.getOffsetY(), null);
            }
        }
        g.dispose();
        compositeCache = out;
        compositeDirty = false;
        return compositeCache;
    }

    public BufferedImage getActiveLayerImage() {
        if (activeIndex < 0 || activeIndex >= layers.size()) {
            return null;
        }
        return layers.get(activeIndex).getContent();
    }

    public ImageEditorLayer getActiveLayer() {
        if (activeIndex < 0 || activeIndex >= layers.size()) {
            return null;
        }
        return layers.get(activeIndex);
    }

    public ImageEditorLayer addTopLayerFromPartial(BufferedImage partial, int x, int y, String name) {
        markCompositeDirty();
        BufferedImage full = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = full.createGraphics();
        g.drawImage(partial, x, y, null);
        g.dispose();
        String layerName = name != null ? name : "图层 " + nameSeq++;
        layers.add(new ImageEditorLayer(layerName, full, true));
        activeIndex = layers.size() - 1;
        return layers.get(activeIndex);
    }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < layers.size()) {
            activeIndex = index;
        } else if (index < 0) {
            activeIndex = -1;
        }
    }

    public void clearActiveLayer() {
        activeIndex = -1;
    }

    public boolean hasActiveLayer() {
        return activeIndex >= 0 && activeIndex < layers.size();
    }

    public void setVisible(int index, boolean visible) {
        if (index >= 0 && index < layers.size()) {
            layers.get(index).setVisible(visible);
            markCompositeDirty();
        }
    }

    public void moveUp(int index) {
        if (index < 0 || index >= layers.size() - 1) {
            return;
        }
        Collections.swap(layers, index, index + 1);
        if (activeIndex == index) {
            activeIndex++;
        } else if (activeIndex == index + 1) {
            activeIndex--;
        }
    }

    public void moveDown(int index) {
        if (index <= 0 || index >= layers.size()) {
            return;
        }
        Collections.swap(layers, index, index - 1);
        if (activeIndex == index) {
            activeIndex--;
        } else if (activeIndex == index - 1) {
            activeIndex++;
        }
    }

    public int indexOfTopFirst(int topFirstIndex) {
        return layers.size() - 1 - topFirstIndex;
    }

    public int topFirstIndexOf(int layerIndex) {
        return layers.size() - 1 - layerIndex;
    }

    public void resizeCanvas(int newW, int newH) {
        newW = Math.max(1, newW);
        newH = Math.max(1, newH);
        markCompositeDirty();
        for (ImageEditorLayer layer : layers) {
            layer.setContent(ImageEditorUtil.resizeCanvas(layer.getContent(), newW, newH));
        }
        canvasWidth = newW;
        canvasHeight = newH;
    }

    public void resizeImageContent(int newW, int newH) {
        newW = Math.max(1, newW);
        newH = Math.max(1, newH);
        markCompositeDirty();
        for (ImageEditorLayer layer : layers) {
            layer.setContent(ImageEditorUtil.resizeImage(layer.getContent(), newW, newH));
        }
        canvasWidth = newW;
        canvasHeight = newH;
    }

    public List<ImageEditorLayer> getLayersViewTopFirst() {
        List<ImageEditorLayer> view = new ArrayList<>(layers);
        Collections.reverse(view);
        return view;
    }
}
