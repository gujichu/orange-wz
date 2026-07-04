package orange.wz.gui.component.dialog;

import orange.wz.gui.component.FileDialog;
import orange.wz.gui.component.form.data.ExportJsonData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.tools.ExportJsonConfigIni;
import orange.wz.provider.tools.ExportJsonConfigIni.Values;

import javax.swing.*;
import java.io.File;

public final class ExportJsonDialog extends BaseDialog<ExportJsonData> {
    private final Values initialConfig;
    private final JTextField indentField = new JTextField(20);
    private final JTextField pathField = new JTextField(20);

    public ExportJsonDialog(EditPane editPane) {
        super("导出 JSON (MS273)", editPane);
        this.initialConfig = ExportJsonConfigIni.load();
        Values v = this.initialConfig;

        indentField.setText(String.valueOf(v.getIndent()));
        JButton selectBtn = new JButton("选择");
        selectBtn.addActionListener(e -> {
            File folder = FileDialog.chooseOpenFolder("选择导出目录");
            if (folder == null) return;
            pathField.setText(folder.getAbsolutePath());
        });
        pathField.setEditable(false);
        if (!v.getExportPath().isBlank()) {
            pathField.setText(v.getExportPath());
        }

        addRow("缩进数量 (0=紧凑, 2=TMS273)", indentField);
        addRow("导出路径", pathField, selectBtn);
    }

    @Override
    public ExportJsonData getData() {
        if (showDialog() != JOptionPane.OK_OPTION) {
            return null;
        }
        if (pathField.getText().isBlank()) {
            return null;
        }

        int indent;
        try {
            indent = Integer.parseInt(indentField.getText());
        } catch (NumberFormatException e) {
            indent = 2;
        }

        Values current = new Values();
        current.setIndent(indent);
        current.setExportPath(pathField.getText().trim());
        ExportJsonConfigIni.saveIfChanged(initialConfig, current);

        return new ExportJsonData(indent, pathField.getText());
    }
}
