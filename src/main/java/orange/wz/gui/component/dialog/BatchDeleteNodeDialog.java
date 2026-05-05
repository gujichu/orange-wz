package orange.wz.gui.component.dialog;

import orange.wz.gui.component.form.data.BatchDeleteNodeFormData;
import orange.wz.gui.component.panel.EditPane;

import javax.swing.*;
import java.awt.event.HierarchyEvent;

public class BatchDeleteNodeDialog extends BaseDialog<BatchDeleteNodeFormData> {
    protected final JTextField nameField = new JTextField(20);
    protected final JCheckBox oddCheckBox = new JCheckBox("奇数（1，3，5，7，9…）");
    protected final JCheckBox evenCheckBox = new JCheckBox("偶数（0，2，4，6，8，10…）");

    public BatchDeleteNodeDialog(EditPane editPane) {
        super("批量删除节点", editPane);

        addRow("节点名称", nameField);
        addRow("奇数/偶数", oddCheckBox, evenCheckBox);

        nameField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && nameField.isShowing()) {
                SwingUtilities.invokeLater(nameField::requestFocusInWindow);
            }
        });
    }

    @Override
    public BatchDeleteNodeFormData getData() {
        if (showDialog() != JOptionPane.OK_OPTION) {
            return null;
        }
        return new BatchDeleteNodeFormData(
                nameField.getText().trim(),
                oddCheckBox.isSelected(),
                evenCheckBox.isSelected()
        );
    }
}
