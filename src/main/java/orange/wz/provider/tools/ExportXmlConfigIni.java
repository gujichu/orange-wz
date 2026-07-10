package orange.wz.provider.tools;

import lombok.Getter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

/**
 * 持久化「导出 XML」对话框选项到 {@code tools/config.ini}。
 */
public final class ExportXmlConfigIni {

    private static final String RELATIVE_TOOLS_CONFIG = "tools/config.ini";

    @Getter
    public static final class Values {
        private int indent = 2;
        private MediaExportType media = MediaExportType.NONE;
        private boolean linuxLineSeparator;
        /** {@code DEFAULT}、{@code V125} 或 {@code GMS265} */
        private String exportVersion = "DEFAULT";
        private String exportPath = "";

        public void setIndent(int indent) {
            this.indent = indent;
        }

        public void setMedia(MediaExportType media) {
            this.media = media != null ? media : MediaExportType.NONE;
        }

        public void setLinuxLineSeparator(boolean linuxLineSeparator) {
            this.linuxLineSeparator = linuxLineSeparator;
        }

        public void setExportVersion(String exportVersion) {
            this.exportVersion = exportVersion != null ? exportVersion : "DEFAULT";
        }

        public void setExportPath(String exportPath) {
            this.exportPath = exportPath != null ? exportPath : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Values values = (Values) o;
            return indent == values.indent
                    && linuxLineSeparator == values.linuxLineSeparator
                    && media == values.media
                    && Objects.equals(exportVersion, values.exportVersion)
                    && Objects.equals(exportPath, values.exportPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(indent, media, linuxLineSeparator, exportVersion, exportPath);
        }
    }

    private ExportXmlConfigIni() {
    }

    /**
     * 解析后的配置文件路径（用于读写）。
     */
    public static Path resolveConfigPath() {
        Path workDir = Paths.get(System.getProperty("user.dir", ".")).resolve(RELATIVE_TOOLS_CONFIG).normalize();
        if (Files.isRegularFile(workDir)) {
            return workDir.toAbsolutePath();
        }
        try {
            Path jarParent = Paths.get(ExportXmlConfigIni.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            if (jarParent != null) {
                Path bundle = jarParent.resolve(RELATIVE_TOOLS_CONFIG).normalize();
                if (Files.isRegularFile(bundle)) {
                    return bundle.toAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }
        return workDir.toAbsolutePath();
    }

    /**
     * 读取配置；文件不存在或损坏时返回默认值。
     */
    public static Values load() {
        Values v = new Values();
        Path path = resolveConfigPath();
        if (!Files.isRegularFile(path)) {
            return v;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Properties props = new Properties();
            props.load(new StringReader(text));
            v.setIndent(parsePositiveInt(props.getProperty("exportXml.indent"), 2));
            v.setMedia(parseMedia(props.getProperty("exportXml.media")));
            v.setLinuxLineSeparator("linux".equalsIgnoreCase(trimOrEmpty(props.getProperty("exportXml.lineSeparator", "windows"))));
            v.setExportVersion(normalizeVersionKey(props.getProperty("exportXml.version")));
            v.setExportPath(trimOrEmpty(props.getProperty("exportXml.exportPath")));
        } catch (Exception e) {
            // 保持默认
        }
        return v;
    }

    /**
     * 若与 {@code baseline} 不同则写入文件（导出成功后调用）。
     */
    public static void saveIfChanged(Values baseline, Values current) {
        if (baseline.equals(current)) {
            return;
        }
        Path path = resolveConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            props.setProperty("exportXml.indent", String.valueOf(current.getIndent()));
            props.setProperty("exportXml.media", current.getMedia().name());
            props.setProperty("exportXml.lineSeparator", current.isLinuxLineSeparator() ? "linux" : "windows");
            props.setProperty("exportXml.version", current.getExportVersion());
            props.setProperty("exportXml.exportPath", current.getExportPath());
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(w, "Orange WZ export XML — UTF-8");
            }
        } catch (IOException ignored) {
        }
    }

    private static int parsePositiveInt(String raw, int defaultVal) {
        try {
            int n = Integer.parseInt(trimOrEmpty(raw));
            return Math.max(0, n);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static MediaExportType parseMedia(String raw) {
        String t = trimOrEmpty(raw);
        if (t.isEmpty()) {
            return MediaExportType.NONE;
        }
        try {
            return MediaExportType.valueOf(t.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MediaExportType.NONE;
        }
    }

    private static String normalizeVersionKey(String raw) {
        String t = trimOrEmpty(raw);
        if (t.isEmpty()) {
            return "DEFAULT";
        }
        if ("125".equals(t) || "v125".equalsIgnoreCase(t)) {
            return "V125";
        }
        if ("gms265".equalsIgnoreCase(t)) {
            return "GMS265";
        }
        return "DEFAULT";
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
