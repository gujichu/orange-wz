package orange.wz.gui.utils;

import orange.wz.gui.MainFrame;
import orange.wz.gui.component.panel.CenterPane;
import orange.wz.gui.component.panel.EditPane;

/**
 * 释放 GUI 侧占用较大的预览缓存（{@link orange.wz.gui.utils.ImagePreviewCache}、预览面板像素）。
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
        pane.getImagePreviewCache().clear();
        pane.getNodeForm().hidePreview();
    }
}
