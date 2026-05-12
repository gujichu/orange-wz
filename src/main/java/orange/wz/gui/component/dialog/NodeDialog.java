package orange.wz.gui.component.dialog;

import orange.wz.gui.component.form.data.NodeFormData;
import orange.wz.gui.component.panel.EditPane;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.HierarchyEvent;

public class NodeDialog extends BaseDialog<NodeFormData> {
    protected final JTextComponent nameField;

    public NodeDialog(String title, EditPane editPane) {
        this(title, "名称", editPane, false);
    }

    /**
     * @param multilineNames true 时使用多行输入（用于一次添加多个目录 / Image 等）
     */
    public NodeDialog(String title, EditPane editPane, boolean multilineNames) {
        this(title, "名称", editPane, multilineNames);
    }

    public NodeDialog(String title, String fieldName, EditPane editPane) {
        this(title, fieldName, editPane, false);
    }

    private NodeDialog(String title, String fieldName, EditPane editPane, boolean multilineNames) {
        super(title, editPane);

        if (multilineNames) {
            JTextArea nameArea = new JTextArea(5, 24);
            nameArea.setLineWrap(true);
            nameArea.setWrapStyleWord(true);
            nameField = nameArea;
            JScrollPane scroll = new JScrollPane(nameField);
            scroll.setPreferredSize(new Dimension(320, 110));
            addRow(fieldName, scroll);
        } else {
            nameField = new JTextField(20);
            addRow(fieldName, nameField);
        }

        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
    }

    @Override
    public NodeFormData getData() {
        if (showDialog() != JOptionPane.OK_OPTION) {
            return null;
        }

        return new NodeFormData(nameField.getText().trim(), "List");
    }
}
