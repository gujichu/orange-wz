package orange.wz.gui.utils;

import orange.wz.gui.MainFrame;
import orange.wz.gui.component.form.impl.CanvasForm;
import orange.wz.gui.component.panel.CenterPane;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.component.panel.ImagePanel;

import java.awt.image.BufferedImage;

/**
 * 释放 GUI 侧占用较大的预览缓存（{@link ImagePreviewCache}、动画预览面板、画布大图等）。
 */
public final class MemoryReclaimer {

    private MemoryReclaimer() {
    }

    /**
     * 清空左右编辑区中的图片/动画预览缓存并隐藏预览面板。
     */
    public static void reclaimAll(MainFrame frame) {
        if (frame == null) {
            return;
        }
        CenterPane cp = frame.getCenterPane();
        if (cp == null) {
            return;
        }
        reclaimEditPane(cp.getLeftEditPane());
        reclaimEditPane(cp.getRightEditPane());
    }

    private static void reclaimEditPane(EditPane pane) {
        if (pane == null) {
            return;
        }
        try {
            pane.getNodeForm().getPreviewContainer().cancelPendingPreviewLoads();
        } catch (Throwable ignored) {
        }
        pane.getImagePreviewCache().clear();
        try {
            pane.discardLoadedWzDecodedCaches();
        } catch (Throwable ignored) {
        }
        flushCanvasFormImage(pane.getCanvasForm());
        pane.getNodeForm().hidePreview();
    }

    private static void flushCanvasFormImage(CanvasForm form) {
        if (form == null || form.getImagePanel() == null) {
            return;
        }
        try {
            ImagePanel ip = form.getImagePanel();
            BufferedImage img = ip.getImage();
            if (img != null) {
                img.flush();
            }
            ip.setImage(null);
            ip.repaint();
        } catch (Throwable ignored) {
        }
    }
}
