package orange.wz.gui.utils;

import orange.wz.provider.tools.ExportXmlConfigIni;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 动画预览工具条选项，读写 {@code tools/config.ini}（与导出 XML 等同路径，合并写入）。
 */
public final class AnimationPreviewConfigIni {

    private static final String K_CACHE = "preview.cacheEnabled";
    private static final String K_ENGLISH = "preview.includeEnglishNamed";
    private static final String K_NUMERIC = "preview.includeNumericNamed";
    private static final String K_ICON = "preview.includeIconNamed";

    private static volatile AnimationPreviewOptions options = AnimationPreviewOptions.defaults();

    static {
        reloadFromDisk();
    }

    private AnimationPreviewConfigIni() {
    }

    public static AnimationPreviewOptions getOptions() {
        return options;
    }

    /**
     * 更新内存中的选项并合并写入 config.ini。
     */
    public static void updateOptions(AnimationPreviewOptions next) {
        if (next == null) {
            return;
        }
        options = next.copy();
        mergeSave();
    }

    public static void reloadFromDisk() {
        options = loadFromDisk();
    }

    private static AnimationPreviewOptions loadFromDisk() {
        AnimationPreviewOptions o = AnimationPreviewOptions.defaults();
        Path path = ExportXmlConfigIni.resolveConfigPath();
        if (!Files.isRegularFile(path)) {
            return o;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Properties props = new Properties();
            props.load(new StringReader(text));
            o.setPreviewCacheEnabled(parseBool(props.getProperty(K_CACHE), true));
            o.setIncludeEnglishNamed(parseBool(props.getProperty(K_ENGLISH), true));
            o.setIncludeNumericNamed(parseBool(props.getProperty(K_NUMERIC), false));
            o.setIncludeIconNamed(parseBool(props.getProperty(K_ICON), false));
        } catch (Exception ignored) {
        }
        return o;
    }

    private static boolean parseBool(String raw, boolean defaultVal) {
        if (raw == null) {
            return defaultVal;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return defaultVal;
        }
        if ("true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t)) {
            return true;
        }
        if ("false".equalsIgnoreCase(t) || "0".equals(t) || "no".equalsIgnoreCase(t)) {
            return false;
        }
        return defaultVal;
    }

    private static void mergeSave() {
        Path path = ExportXmlConfigIni.resolveConfigPath();
        Properties props = new Properties();
        try {
            if (Files.isRegularFile(path)) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                props.load(new StringReader(text));
            }
        } catch (Exception ignored) {
        }
        AnimationPreviewOptions o = options;
        props.setProperty(K_CACHE, String.valueOf(o.isPreviewCacheEnabled()));
        props.setProperty(K_ENGLISH, String.valueOf(o.isIncludeEnglishNamed()));
        props.setProperty(K_NUMERIC, String.valueOf(o.isIncludeNumericNamed()));
        props.setProperty(K_ICON, String.valueOf(o.isIncludeIconNamed()));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(w, "Orange WZ — UTF-8");
            }
        } catch (IOException ignored) {
        }
    }
}
