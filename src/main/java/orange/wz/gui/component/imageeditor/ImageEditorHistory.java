package orange.wz.gui.component.imageeditor;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class ImageEditorHistory {

    private static final int MAX = 50;
    private static final int THUMB = 48;

    public record Entry(String label, BufferedImage image, ImageIcon thumbnail) {
    }

    private final List<Entry> entries = new ArrayList<>();
    @Getter
    private int currentIndex = -1;
    private Runnable structureChangedListener;
    private Runnable indexChangedListener;

    public void setChangeListener(Runnable listener) {
        this.structureChangedListener = listener;
        this.indexChangedListener = listener;
    }

    public void setStructureChangedListener(Runnable listener) {
        this.structureChangedListener = listener;
    }

    public void setIndexChangedListener(Runnable listener) {
        this.indexChangedListener = listener;
    }

    public void clear() {
        entries.clear();
        currentIndex = -1;
        fireStructureChanged();
    }

    public void push(String label, BufferedImage image) {
        if (image == null) {
            return;
        }
        while (entries.size() > currentIndex + 1) {
            entries.removeLast();
        }
        BufferedImage copy = ImageEditorUtil.deepCopy(image);
        entries.add(new Entry(label, copy, createThumbnail(copy)));
        currentIndex = entries.size() - 1;
        while (entries.size() > MAX) {
            entries.removeFirst();
            currentIndex--;
        }
        fireStructureChanged();
    }

    public boolean canStepBack() {
        return currentIndex > 0;
    }

    public boolean canStepForward() {
        return currentIndex >= 0 && currentIndex < entries.size() - 1;
    }

    public BufferedImage stepBack() {
        if (!canStepBack()) {
            return currentSnapshot();
        }
        currentIndex--;
        fireIndexChanged();
        return currentSnapshot();
    }

    public BufferedImage stepForward() {
        if (!canStepForward()) {
            return currentSnapshot();
        }
        currentIndex++;
        fireIndexChanged();
        return currentSnapshot();
    }

    public BufferedImage goTo(int index) {
        if (index < 0 || index >= entries.size()) {
            return currentSnapshot();
        }
        currentIndex = index;
        fireIndexChanged();
        return currentSnapshot();
    }

    public BufferedImage currentSnapshot() {
        if (currentIndex < 0 || currentIndex >= entries.size()) {
            return null;
        }
        return entries.get(currentIndex).image();
    }

    public BufferedImage copyCurrentSnapshot() {
        BufferedImage snap = currentSnapshot();
        return snap == null ? null : ImageEditorUtil.deepCopy(snap);
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    private void fireStructureChanged() {
        if (structureChangedListener != null) {
            structureChangedListener.run();
        }
    }

    private void fireIndexChanged() {
        if (indexChangedListener != null) {
            indexChangedListener.run();
        }
    }

    private static ImageIcon createThumbnail(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double scale = Math.min((double) THUMB / w, (double) THUMB / h);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage thumb = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        g.setColor(new Color(0xCCCCCC));
        g.fillRect(0, 0, THUMB, THUMB);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, (THUMB - tw) / 2, (THUMB - th) / 2, tw, th, null);
        g.dispose();
        return new ImageIcon(thumb);
    }
}
