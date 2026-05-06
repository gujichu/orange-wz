package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFolder;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 释放已加载 Wz 对象持有的解码图片、reader 等，避免卸载树节点后仍占用堆。
 */
@Slf4j
public final class WzLoadedMemoryReclaimer {

    private WzLoadedMemoryReclaimer() {
    }

    /**
     * 从树上摘下节点前调用：彻底清空对应 {@link WzObject}（含解码图、BinaryReader）。
     */
    public static void releaseRoot(WzObject obj) {
        if (obj == null) {
            return;
        }
        try {
            if (obj instanceof WzDirectory dir) {
                dir.clear();
                return;
            }
            if (obj instanceof WzImage img) {
                img.clear();
                return;
            }
            if (obj instanceof WzFolder folder) {
                List<WzObject> kids = new ArrayList<>(folder.getChildren());
                for (WzObject k : kids) {
                    releaseRoot(k);
                    folder.remove(k);
                }
            }
        } catch (Throwable t) {
            log.warn("释放 Wz 资源失败: {}", obj.getPath(), t);
        }
    }

    /**
     * 文件仍打开时丢弃已解码 PNG（及可再从 reader 重建的压缩字节副本），供「内存回收」菜单使用。
     */
    public static void discardDecodedImagesUnder(DefaultMutableTreeNode root) {
        if (root == null) {
            return;
        }
        Object uo = root.getUserObject();
        if (uo instanceof WzObject wz) {
            discardDecodedRecursive(wz);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            discardDecodedImagesUnder((DefaultMutableTreeNode) root.getChildAt(i));
        }
    }

    private static void discardDecodedRecursive(WzObject obj) {
        if (obj instanceof WzDirectory dir) {
            for (WzDirectory sub : dir.getDirectories()) {
                discardDecodedRecursive(sub);
            }
            for (WzImage img : dir.getImages()) {
                discardDecodedRecursive(img);
            }
            return;
        }
        if (obj instanceof WzImage img) {
            for (WzImageProperty p : img.getChildren()) {
                discardDecodedInProperty(p);
            }
            return;
        }
        if (obj instanceof WzFolder folder) {
            for (WzObject k : folder.getChildren()) {
                discardDecodedRecursive(k);
            }
        }
    }

    private static void discardDecodedInProperty(WzImageProperty p) {
        if (p == null) {
            return;
        }
        if (p instanceof WzCanvasProperty canvas) {
            canvas.discardHeavyGraphicCaches();
            return;
        }
        if (!p.isListProperty()) {
            return;
        }
        for (WzImageProperty ch : p.getChildren()) {
            discardDecodedInProperty(ch);
        }
    }
}
