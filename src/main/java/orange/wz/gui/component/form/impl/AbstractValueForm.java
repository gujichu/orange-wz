package orange.wz.gui.component.form.impl;

import lombok.Getter;
import orange.wz.gui.component.form.FormSaveHandler;
import orange.wz.gui.component.form.data.NodeFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.SkillPropertyInfoJson;
import orange.wz.provider.WzObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public abstract class AbstractValueForm {
    protected final static int defaultColumns = 30;
    @Getter
    protected final JPanel valuePane = new JPanel(new BorderLayout());
    protected final JPanel topLeftPanel = new JPanel(new GridBagLayout());
    protected final JPanel bottomRightPanel = new JPanel(new GridBagLayout());
    protected final JTextField nameInput = new JTextField(defaultColumns);
    protected final JTextField typeInput = new JTextField(defaultColumns);

    private final JPanel meaningPanel = new JPanel(new BorderLayout());
    private final JTextArea propertyMeaningArea = new JTextArea(3, 20);

    private int topPanelRow = 0;
    private int bottomPanelCol = 0;

    private EditPane editPane;
    private WzObject curWzObject;

    protected EditPane getEditPane() {
        return editPane;
    }

    protected WzObject getCurWzObject() {
        return curWzObject;
    }

    protected AbstractValueForm() {
        propertyMeaningArea.setEditable(false);
        propertyMeaningArea.setOpaque(false);
        propertyMeaningArea.setLineWrap(true);
        propertyMeaningArea.setWrapStyleWord(true);
        JScrollPane meaningScroll = new JScrollPane(propertyMeaningArea);
        meaningScroll.setBorder(BorderFactory.createEmptyBorder());
        meaningPanel.setBorder(new TitledBorder("含义"));
        meaningPanel.add(meaningScroll, BorderLayout.CENTER);
        meaningPanel.setVisible(false);

        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.add(topLeftPanel, BorderLayout.CENTER);
        topWrap.add(meaningPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(topWrap, BorderLayout.CENTER);
        valuePane.add(topPanel, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomRightPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        bottomPanel.add(bottomRightPanel, BorderLayout.EAST);
        valuePane.add(bottomPanel, BorderLayout.SOUTH);

        topLeftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        addRow("名称:", nameInput);

        typeInput.setEditable(false);
        addRow("类型:", typeInput);

        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> FormSaveHandler.saveClick(curWzObject, editPane));
        addButton(saveBtn);
    }

    protected void addRow(String label, JComponent field) {
        // 标签 gbc
        GridBagConstraints labelGbc = baseGbc();
        labelGbc.gridx = 0;
        labelGbc.gridy = topPanelRow;
        labelGbc.weightx = 0; // 标签不拉伸
        topLeftPanel.add(new JLabel(label), labelGbc);

        // 输入框 gbc
        GridBagConstraints fieldGbc = baseGbc();
        fieldGbc.gridx = 1;
        fieldGbc.gridy = topPanelRow;
        fieldGbc.weightx = 1.0; // 输入框水平拉伸
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        topLeftPanel.add(field, fieldGbc);

        topPanelRow++;
    }

    protected void addButton(JButton button) {
        // 标签 gbc
        GridBagConstraints gbc = baseGbc();
        gbc.gridx = bottomPanelCol;
        gbc.gridy = 0;
        bottomRightPanel.add(button, gbc);

        bottomPanelCol++;
    }

    protected GridBagConstraints baseGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        return gbc;
    }

    protected void setData(String name, String type, WzObject wzObject, EditPane editPane) {
        nameInput.setText(name);
        typeInput.setText(type);
        this.curWzObject = wzObject;
        this.editPane = editPane;
        if (isPropertyMeaningLookupEnabled()) {
            refreshPropertyMeaningHint(name);
        } else {
            refreshPropertyMeaningHint(null);
        }
    }

    /**
     * 为向量、数值、字符串、Lua 等属性表单开启：按节点名称在 info.json 中查找并显示含义。
     */
    protected boolean isPropertyMeaningLookupEnabled() {
        return false;
    }

    private void refreshPropertyMeaningHint(String propertyName) {
        if (propertyName == null) {
            propertyMeaningArea.setText("");
            meaningPanel.setVisible(false);
            return;
        }
        String desc = SkillPropertyInfoJson.lookup(propertyName);
        if (desc != null && !desc.isEmpty()) {
            propertyMeaningArea.setText(desc);
            meaningPanel.setVisible(true);
        } else {
            propertyMeaningArea.setText("");
            meaningPanel.setVisible(false);
        }
    }

    public void onHide() {
        curWzObject = null;
        refreshPropertyMeaningHint(null);
    }

    public abstract NodeFormData getData();
}

