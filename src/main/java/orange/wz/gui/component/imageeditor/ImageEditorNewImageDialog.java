package orange.wz.gui.component.imageeditor;

import javax.swing.*;
import java.awt.*;

final class ImageEditorNewImageDialog extends JDialog {

    private final JSpinner canvasW = new JSpinner(new SpinnerNumberModel(800, 1, 16384, 1));
    private final JSpinner canvasH = new JSpinner(new SpinnerNumberModel(600, 1, 16384, 1));
    private final JSpinner imageW = new JSpinner(new SpinnerNumberModel(800, 1, 16384, 1));
    private final JSpinner imageH = new JSpinner(new SpinnerNumberModel(600, 1, 16384, 1));
    private boolean confirmed;

    ImageEditorNewImageDialog(Window owner) {
        super(owner, "新建图片", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(form, gbc, 0, "画布宽度", canvasW);
        addRow(form, gbc, 1, "画布高度", canvasH);
        addRow(form, gbc, 2, "图片宽度", imageW);
        addRow(form, gbc, 3, "图片高度", imageH);

        JLabel hint = new JLabel("<html><small>画布为文档尺寸；图片为初始内容尺寸。透明背景，可直接编辑保存。</small></html>");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        form.add(hint, gbc);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton ok = new JButton("创建");
        JButton cancel = new JButton("取消");
        ok.addActionListener(e -> {
            confirmed = true;
            setVisible(false);
        });
        cancel.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
        });
        buttons.add(ok);
        buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(ok);
    }

    boolean showAndConfirm() {
        confirmed = false;
        setVisible(true);
        return confirmed;
    }

    int getCanvasWidth() {
        return (Integer) canvasW.getValue();
    }

    int getCanvasHeight() {
        return (Integer) canvasH.getValue();
    }

    int getImageWidth() {
        return (Integer) imageW.getValue();
    }

    int getImageHeight() {
        return (Integer) imageH.getValue();
    }

    private static void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        field.setPreferredSize(new Dimension(120, 22));
        form.add(field, gbc);
        gbc.fill = GridBagConstraints.NONE;
    }
}
