package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.dialog.ImageCompareDialog;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzStringProperty;

import java.util.List;
import java.util.Objects;

@Slf4j
public final class ChineseUtil {

    /**
     * 扫描可替换项：路径匹配的 WzStringProperty，来源值非空且与目标不同即列入。
     */
    public static void collectReplacements(WzObject from, WzObject to, List<ChineseReplaceEntry> out) {
        if (from == null || to == null) {
            return;
        }

        if (to instanceof WzFile toFile && from instanceof WzFile fromFile) {
            requireParsed(toFile);
            requireParsed(fromFile);
            toFile.getWzDirectory().getDirectories().forEach(toDir ->
                    collectReplacements(fromFile.getWzDirectory().getDirectory(toDir.getName()), toDir, out));
            toFile.getWzDirectory().getImages().forEach(toImage ->
                    collectReplacements(fromFile.getWzDirectory().getImage(toImage.getName()), toImage, out));
        } else if (to instanceof WzDirectory toDirectory && from instanceof WzDirectory fromDirectory) {
            if (toDirectory.isWzFile()) {
                requireParsed(toDirectory.getWzFile());
            }
            if (fromDirectory.isWzFile()) {
                requireParsed(fromDirectory.getWzFile());
            }
            toDirectory.getDirectories().forEach(toDir ->
                    collectReplacements(fromDirectory.getDirectory(toDir.getName()), toDir, out));
            toDirectory.getImages().forEach(toImage ->
                    collectReplacements(fromDirectory.getImage(toImage.getName()), toImage, out));
        } else if (to instanceof WzImage toImage && from instanceof WzImage fromImage) {
            requireParsed(toImage);
            requireParsed(fromImage);
            toImage.getChildren().forEach(img -> collectReplacements(fromImage.getChild(img.getName()), img, out));
        } else if (to instanceof WzListProperty toListProperty && from instanceof WzListProperty fromList) {
            toListProperty.getChildren().forEach(prop ->
                    collectReplacements(fromList.getChild(prop.getName()), prop, out));
        } else if (to instanceof WzStringProperty toString && from instanceof WzStringProperty fromString) {
            String fromValue = fromString.getValue();
            if (fromValue == null) {
                return;
            }
            String toValue = toString.getValue();
            if (Objects.equals(fromValue, toValue)) {
                return;
            }
            out.add(new ChineseReplaceEntry(toString.getPath(), toString, toValue, fromValue));
        }
    }

    /** 直接批量替换（无预览窗口），规则与 {@link #collectReplacements} 一致。 */
    public static void chinese(WzObject from, WzObject to) {
        if (from == null || to == null) {
            return;
        }
        java.util.ArrayList<ChineseReplaceEntry> entries = new java.util.ArrayList<>();
        collectReplacements(from, to, entries);
        for (ChineseReplaceEntry entry : entries) {
            entry.apply();
        }
    }

    public static boolean isChineseStr(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return !str.matches(".*[\\uAC00-\\uD7A3].*")
                && str.matches(".*[\\u4e00-\\u9fa5].*");
    }

    private static ImageCompareDialog imageCompareDialog;

    public static void initChineseImg(EditPane toEditPane, EditPane fromEditPane) {
        imageCompareDialog = new ImageCompareDialog(MainFrame.getInstance(), toEditPane, fromEditPane);
    }

    public static void completeChineseImg() {
        imageCompareDialog.completeScan();
    }

    public static void chineseImg(WzObject from, WzObject to) {
        if (from == null || to == null) {
            return;
        }

        if (to instanceof WzDirectory toDirectory && from instanceof WzDirectory fromDirectory) {
            if (toDirectory.isWzFile() && !toDirectory.getWzFile().parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", toDirectory.getWzFile().getName(), toDirectory.getWzFile().getStatus().getMessage());
                throw new RuntimeException();
            }
            if (fromDirectory.isWzFile() && !fromDirectory.getWzFile().parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", fromDirectory.getWzFile().getName(), fromDirectory.getWzFile().getStatus().getMessage());
                throw new RuntimeException();
            }

            toDirectory.getDirectories().forEach(toDir -> chineseImg(fromDirectory.getDirectory(toDir.getName()), toDir));
            toDirectory.getImages().forEach(toImage -> chineseImg(fromDirectory.getImage(toImage.getName()), toImage));
        } else if (to instanceof WzImage toImage && from instanceof WzImage fromImage) {
            if (!toImage.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", toImage.getName(), toImage.getStatus().getMessage());
                throw new RuntimeException();
            }
            if (!fromImage.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", fromImage.getName(), fromImage.getStatus().getMessage());
                throw new RuntimeException();
            }
            toImage.getChildren().forEach(img -> chineseImg(fromImage.getChild(img.getName()), img));
        } else if (to instanceof WzListProperty toListProperty && from instanceof WzListProperty fromList) {
            toListProperty.getChildren().forEach(prop -> chineseImg(fromList.getChild(prop.getName()), prop));
        } else if (to instanceof WzCanvasProperty toCav && from instanceof WzCanvasProperty fromCav) {
            if (fromCav.getHeight() == 1 && fromCav.getWidth() == 1) {
                fromCav.clearImage();
                toCav.clearImage();
                log.info("{} 来源图片为 1x1 空白图片，已跳过", fromCav.getPath());
            } else {
                byte[] fromBytes = fromCav.getImageBytes(false);
                byte[] toBytes = toCav.getImageBytes(false);
                double diff = (fromBytes == null || toBytes == null) ? 1.0 : differenceRate(fromBytes, toBytes);
                boolean identical = fromCav.getWidth() == toCav.getWidth()
                        && fromCav.getHeight() == toCav.getHeight()
                        && fromCav.getFormat() == toCav.getFormat()
                        && fromCav.getScale() == toCav.getScale()
                        && diff == 0;
                imageCompareDialog.addCompare(toCav, fromCav);
                if (!identical) {
                    log.debug("{} 差异率 {}", to.getPath(), diff);
                }
                if (identical) {
                    fromCav.clearImage();
                    toCav.clearImage();
                }
            }
        }
    }

    private static void requireParsed(WzFile file) {
        if (!file.parse()) {
            MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", file.getName(), file.getStatus().getMessage());
            throw new RuntimeException();
        }
    }

    private static void requireParsed(WzImage image) {
        if (!image.parse()) {
            MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", image.getName(), image.getStatus().getMessage());
            throw new RuntimeException();
        }
    }

    public static double differenceRate(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 1.0;
        }

        int diffCount = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                diffCount++;
            }
        }
        return (double) diffCount / a.length;
    }
}
