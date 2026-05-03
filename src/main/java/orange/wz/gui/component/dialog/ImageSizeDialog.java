package orange.wz.gui.component.dialog;

import orange.wz.gui.component.panel.EditPane;

import javax.swing.*;

public class ImageSizeDialog extends BaseDialog<int[]> {
    private final JTextField widthField = new JTextField(20);
    private final JTextField heightField = new JTextField(20);

    public ImageSizeDialog(EditPane editPane, int defaultWidth, int defaultHeight) {
        super("图片大小", editPane);
        widthField.setText(String.valueOf(defaultWidth));
        heightField.setText(String.valueOf(defaultHeight));
        addRow("图片宽度", widthField);
        addRow("图片高度", heightField);
    }

    @Override
    public int[] getData() {
        if (showDialog() != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            int width = Integer.parseInt(widthField.getText().trim());
            int height = Integer.parseInt(heightField.getText().trim());
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new int[]{width, height};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
