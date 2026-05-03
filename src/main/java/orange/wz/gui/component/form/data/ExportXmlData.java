package orange.wz.gui.component.form.data;

import lombok.Getter;
import orange.wz.provider.tools.MediaExportType;

@Getter
public final class ExportXmlData {
    public enum ExportVersion {
        DEFAULT,
        V125
    }

    private final int indent;
    private final MediaExportType meType;
    private final String exportPath;
    private final boolean linux;
    private final ExportVersion version;

    public ExportXmlData(int indent, MediaExportType meType, String exportPath, boolean linux, ExportVersion version) {
        this.indent = indent;
        this.meType = meType;
        this.exportPath = exportPath;
        this.linux = linux;
        this.version = version;
    }
}
