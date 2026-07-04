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
 * 持久化「导出 JSON」对话框选项到 {@code tools/config.ini}。
 */
public final class ExportJsonConfigIni {

    private ExportJsonConfigIni() {
    }

    @Getter
    public static final class Values {
        private int indent = 2;
        private String exportPath = "";

        public void setIndent(int indent) {
            this.indent = indent;
        }

        public void setExportPath(String exportPath) {
            this.exportPath = exportPath != null ? exportPath : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Values values = (Values) o;
            return indent == values.indent && Objects.equals(exportPath, values.exportPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(indent, exportPath);
        }
    }

    public static Path resolveConfigPath() {
        return ExportXmlConfigIni.resolveConfigPath();
    }

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
            v.setIndent(parsePositiveInt(props.getProperty("exportJson.indent"), 2));
            v.setExportPath(trimOrEmpty(props.getProperty("exportJson.exportPath")));
        } catch (Exception ignored) {
        }
        return v;
    }

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
            if (Files.isRegularFile(path)) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                props.load(new StringReader(text));
            }
            props.setProperty("exportJson.indent", String.valueOf(current.getIndent()));
            props.setProperty("exportJson.exportPath", current.getExportPath());
            try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(w, "Orange WZ — UTF-8");
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

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
