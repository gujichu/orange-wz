package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.dialog.ImageCompareDialog;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzStringProperty;

@Slf4j
public final class ChineseUtil {
    public static void chinese(WzObject from, WzObject to) {
        if (from == null || to == null) return;

        if (to instanceof WzFile toFile && from instanceof WzFile fromFile) {
            if (!toFile.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", toFile.getName(), toFile.getStatus().getMessage());
                throw new RuntimeException();
            }
            if (!fromFile.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", fromFile.getName(), fromFile.getStatus().getMessage());
                throw new RuntimeException();
            }

            toFile.getWzDirectory().getDirectories().forEach(toDir -> chinese(fromFile.getWzDirectory().getDirectory(toDir.getName()), toDir));
            toFile.getWzDirectory().getImages().forEach(toImage -> chinese(fromFile.getWzDirectory().getImage(toImage.getName()), toImage));
        } else if (to instanceof WzDirectory toDirectory && from instanceof WzDirectory fromDirectory) {
            toDirectory.getDirectories().forEach(toDir -> chinese(fromDirectory.getDirectory(toDir.getName()), toDir));
            toDirectory.getImages().forEach(toImage -> chinese(fromDirectory.getImage(toImage.getName()), toImage));
        } else if (to instanceof WzImage toImage && from instanceof WzImage fromImage) {
            if (!toImage.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", toImage.getName(), toImage.getStatus().getMessage());
                throw new RuntimeException();
            }
            if (!fromImage.parse()) {
                MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", fromImage.getName(), fromImage.getStatus().getMessage());
                throw new RuntimeException();
            }
            toImage.getChildren().forEach(img -> chinese(fromImage.getChild(img.getName()), img));
        } else if (to instanceof WzListProperty toListProperty && from instanceof WzListProperty fromList) {
            toListProperty.getChildren().forEach(prop -> chinese(fromList.getChild(prop.getName()), prop));
        } else if (to instanceof WzStringProperty toString && from instanceof WzStringProperty fromString) {
            String fromValue = fromString.getValue();
            if (fromValue != null && !isChineseStr(toString.getValue()) && isChineseStr(fromValue)) {
                toString.setTempChanged(true);
                toString.getWzImage().setChanged(true);
                toString.setValue(fromValue);
            }
        }
    }

    public static boolean isChineseStr(String str) {
        return !str.matches(".*[\\uAC00-\\uD7A3].*")  // 不能有韩文
                && str.matches(".*[\\u4e00-\\u9fa5].*");  // 有中文字符
    }

    private static ImageCompareDialog imageCompareDialog;

    public static void initChineseImg() {
        imageCompareDialog = new ImageCompareDialog(MainFrame.getInstance());
    }

    public static void completeChineseImg() {
        imageCompareDialog.completeScan();
    }

    public static void chineseImg(WzObject from, WzObject to) {
        if (from == null || to == null) return;

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
                // 来源方没有图片
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
                // 路径一致时也列入对比界面，避免「扫描完毕找不到数据」；完全一致仍可释放解码缓存
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

    /**
     * 计算两个 byte 数组的差异率
     *
     * @param a 第一个数组
     * @param b 第二个数组
     * @return 差异率，范围 0.0 ~ 1.0
     * @throws IllegalArgumentException 如果数组长度不同
     */
    public static double differenceRate(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return 1.0;
        }

        int diffCount = 0;
        int len = a.length;

        // 用简单循环比较
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                diffCount++;
            }
        }

        return (double) diffCount / len;
    }
}
