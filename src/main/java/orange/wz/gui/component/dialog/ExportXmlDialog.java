package orange.wz.gui.component.dialog;

import orange.wz.gui.component.FileDialog;
import orange.wz.gui.component.form.data.ExportXmlData;
import orange.wz.gui.component.form.data.ExportXmlData.ExportVersion;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.tools.ExportXmlConfigIni;
import orange.wz.provider.tools.ExportXmlConfigIni.Values;
import orange.wz.provider.tools.MediaExportType;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class ExportXmlDialog extends BaseDialog<ExportXmlData> {
    private final Values initialConfig;
    private final JTextField indentField = new JTextField(20);
    private final JRadioButton noneRadio = new JRadioButton("不输出");
    private final JRadioButton base64Radio = new JRadioButton("Base64");
    private final JRadioButton fileRadio = new JRadioButton("文件");
    private final JRadioButton windowsRadio = new JRadioButton("Windows CRLF \\r\\n");
    private final JRadioButton linuxRadio = new JRadioButton("Linux LF \\n");
    private final JTextField pathField = new JTextField(20);
    private final JRadioButton defaultRadio = new JRadioButton("默认");
    private final JRadioButton v125Radio = new JRadioButton("125");
    private final JRadioButton gms265Radio = new JRadioButton("GMS265");

    public ExportXmlDialog(EditPane editPane) {
        super("导出 XML", editPane);
        this.initialConfig = ExportXmlConfigIni.load();
        Values v = this.initialConfig;

        indentField.setText(String.valueOf(v.getIndent()));
        JButton selectBtn = new JButton("选择");
        selectBtn.setSelected(false);
        selectBtn.addActionListener(e -> {
            File folder = FileDialog.chooseOpenFolder("选择导出目录");
            if (folder == null) return;

            pathField.setText(folder.getAbsolutePath());
        });
        pathField.setEditable(false);
        if (!v.getExportPath().isBlank()) {
            pathField.setText(v.getExportPath());
        }

        addRow("缩进数量", indentField);
        // 创建互斥单选集合
        ButtonGroup mediaGroup = new ButtonGroup();
        mediaGroup.add(noneRadio);
        mediaGroup.add(base64Radio);
        mediaGroup.add(fileRadio);
        switch (v.getMedia()) {
            case BASE64 -> base64Radio.setSelected(true);
            case FILE -> fileRadio.setSelected(true);
            default -> noneRadio.setSelected(true);
        }

        JPanel mediaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mediaPanel.add(noneRadio);
        mediaPanel.add(base64Radio);
        mediaPanel.add(fileRadio);
        addRow("图片音频", mediaPanel);

        // 创建互斥单选集合
        ButtonGroup lineSepGroup = new ButtonGroup();
        lineSepGroup.add(windowsRadio);
        lineSepGroup.add(linuxRadio);

        if (v.isLinuxLineSeparator()) {
            linuxRadio.setSelected(true);
        } else {
            windowsRadio.setSelected(true);
        }

        JPanel lineSepPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineSepPanel.add(windowsRadio);
        lineSepPanel.add(linuxRadio);
        addRow("换行符", lineSepPanel);

        ButtonGroup versionGroup = new ButtonGroup();
        versionGroup.add(defaultRadio);
        versionGroup.add(v125Radio);
        versionGroup.add(gms265Radio);
        if ("GMS265".equals(v.getExportVersion())) {
            gms265Radio.setSelected(true);
        } else if ("V125".equals(v.getExportVersion())) {
            v125Radio.setSelected(true);
        } else {
            defaultRadio.setSelected(true);
        }

        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        versionPanel.add(defaultRadio);
        versionPanel.add(v125Radio);
        versionPanel.add(gms265Radio);
        addRow("导出版本", versionPanel);

        addRow("导出路径", pathField, selectBtn);
    }

    @Override
    public ExportXmlData getData() {
        if (showDialog() != JOptionPane.OK_OPTION) {
            return null;
        }

        if (pathField.getText().isBlank()) return null;

        int indent;
        try {
            indent = Integer.parseInt(indentField.getText());
        } catch (NumberFormatException e) {
            indent = 0;
        }

        MediaExportType meType = MediaExportType.NONE;
        if (base64Radio.isSelected()) {
            meType = MediaExportType.BASE64;
        } else if (fileRadio.isSelected()) {
            meType = MediaExportType.FILE;
        }

        boolean linux = linuxRadio.isSelected();

        ExportVersion version;
        if (gms265Radio.isSelected()) {
            version = ExportVersion.GMS265;
        } else if (v125Radio.isSelected()) {
            version = ExportVersion.V125;
        } else {
            version = ExportVersion.DEFAULT;
        }

        Values current = new Values();
        current.setIndent(indent);
        current.setMedia(meType);
        current.setLinuxLineSeparator(linux);
        current.setExportVersion(version.name());
        current.setExportPath(pathField.getText().trim());
        ExportXmlConfigIni.saveIfChanged(initialConfig, current);

        return new ExportXmlData(indent, meType, pathField.getText(), linux, version);
    }

}
