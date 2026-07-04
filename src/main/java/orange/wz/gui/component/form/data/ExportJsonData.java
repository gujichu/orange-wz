package orange.wz.gui.component.form.data;

import lombok.Getter;

@Getter
public final class ExportJsonData {
    private final int indent;
    private final String exportPath;

    public ExportJsonData(int indent, String exportPath) {
        this.indent = indent;
        this.exportPath = exportPath;
    }
}
