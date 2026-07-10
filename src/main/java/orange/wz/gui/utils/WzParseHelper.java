package orange.wz.gui.utils;

import orange.wz.gui.MainFrame;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzMsImageFile;
import orange.wz.provider.WzXmlFile;

/**
 * 统一的 Wz 解析失败处理：设置状态栏，避免在菜单/EDT 中抛出空 {@link RuntimeException}。
 */
public final class WzParseHelper {

    public static final class ParseFailedException extends RuntimeException {
        public ParseFailedException(String message) {
            super(message);
        }
    }

    private WzParseHelper() {
    }

    public static boolean ensureParsed(WzFile file) {
        if (file.parse()) {
            return true;
        }
        reportFailure(file.getName(), file.getStatus().getMessage());
        return false;
    }

    public static boolean ensureParsed(WzImage image) {
        if (image.parse()) {
            return true;
        }
        reportFailure(image.getName(), image.getStatus().getMessage());
        return false;
    }

    public static boolean ensureParsed(WzMsImageFile file) {
        if (file.parse()) {
            return true;
        }
        reportFailure(file.getName(), file.getStatus().getMessage());
        return false;
    }

    public static void requireParsed(WzFile file) {
        if (!ensureParsed(file)) {
            throw new ParseFailedException(parseFailureMessage(file.getName(), file.getStatus().getMessage()));
        }
    }

    public static void requireParsed(WzImage image) {
        if (!ensureParsed(image)) {
            throw new ParseFailedException(parseFailureMessage(image.getName(), image.getStatus().getMessage()));
        }
    }

    public static void requireParsed(WzMsImageFile file) {
        if (!ensureParsed(file)) {
            throw new ParseFailedException(parseFailureMessage(file.getName(), file.getStatus().getMessage()));
        }
    }

    public static boolean ensureParsed(WzImage image, boolean realParse) {
        if (image.parse(realParse)) {
            return true;
        }
        reportFailure(image.getName(), image.getStatus().getMessage());
        return false;
    }

    public static void requireParsed(WzImage image, boolean realParse) {
        if (!ensureParsed(image, realParse)) {
            throw new ParseFailedException(parseFailureMessage(image.getName(), image.getStatus().getMessage()));
        }
    }

    public static boolean ensureParsed(WzXmlFile file) {
        if (file.parse()) {
            return true;
        }
        reportFailure(file.getName(), file.getStatus().getMessage());
        return false;
    }

    public static void requireParsed(WzXmlFile file) {
        if (!ensureParsed(file)) {
            throw new ParseFailedException(parseFailureMessage(file.getName(), file.getStatus().getMessage()));
        }
    }

    private static void reportFailure(String name, String message) {
        MainFrame.getInstance().setStatusText(parseFailureMessage(name, message));
    }

    private static String parseFailureMessage(String name, String message) {
        return String.format("文件 %s 解析失败: %s", name, message);
    }
}
