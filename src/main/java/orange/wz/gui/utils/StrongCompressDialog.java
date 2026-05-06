package orange.wz.gui.utils;

import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzPngZlibCompressMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.zip.Deflater;

/**
 * 强力压缩前的参数对话框。
 */
public final class StrongCompressDialog {

    private StrongCompressDialog() {
    }

    public static StrongCompressOptions showDialog(Component parent) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.gridy = 0;

        JComboBox<WzPngFormat> formatBox = new JComboBox<>(new WzPngFormat[]{
                WzPngFormat.ARGB8888,
                WzPngFormat.ARGB4444,
                WzPngFormat.ARGB1555,
                WzPngFormat.RGB565,
                WzPngFormat.DXT3,
                WzPngFormat.DXT5,
        });
        formatBox.setSelectedItem(WzPngFormat.ARGB8888);

        JSlider zlibSlider = new JSlider(1, 9, Deflater.BEST_COMPRESSION);
        zlibSlider.setMajorTickSpacing(2);
        zlibSlider.setMinorTickSpacing(1);
        zlibSlider.setPaintTicks(true);
        zlibSlider.setPaintLabels(true);
        JLabel zlibLabel = new JLabel("ZLIB 级别: " + zlibSlider.getValue() + "（9=最强压缩）");
        zlibSlider.addChangeListener(e -> zlibLabel.setText("ZLIB 级别: " + zlibSlider.getValue() + "（9=最强压缩）"));

        JComboBox<WzPngZlibCompressMode> modeBox = new JComboBox<>(WzPngZlibCompressMode.values());
        modeBox.setSelectedItem(WzPngZlibCompressMode.DEFAULT);
        modeBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof WzPngZlibCompressMode m) {
                    setText(m.displayLabel());
                }
                return this;
            }
        });
        JLabel modeHint = new JLabel("「极限体积」对每张图尝试多种策略取最小 zlib（慢）；渐变图可试「滤波优先」");
        modeHint.setFont(modeHint.getFont().deriveFont(Font.PLAIN, modeHint.getFont().getSize() - 1f));

        JComboBox<String> scaleBox = new JComboBox<>(new String[]{
                "1x（无块缩放，scale=0）",
                "2x 块（scale=1）",
                "4x 块（scale=2）",
        });
        scaleBox.setSelectedIndex(0);
        JLabel scaleHint = new JLabel("仅 ARGB4444 / RGB565 有效；其它格式将使用 1x");
        scaleHint.setFont(scaleHint.getFont().deriveFont(Font.PLAIN, scaleHint.getFont().getSize() - 1f));

        JCheckBox ditherCb = new JCheckBox("高保真抖动（4444 / 565，减轻色带）", true);
        JCheckBox skipCb = new JCheckBox("若压缩后体积未变小则保留原图", true);

        Runnable refreshScaleEnabled = () -> {
            WzPngFormat f = (WzPngFormat) formatBox.getSelectedItem();
            boolean en = f == WzPngFormat.ARGB4444 || f == WzPngFormat.RGB565;
            scaleBox.setEnabled(en);
            ditherCb.setEnabled(f == WzPngFormat.ARGB4444 || f == WzPngFormat.RGB565);
        };
        formatBox.addActionListener(e -> refreshScaleEnabled.run());
        refreshScaleEnabled.run();

        gc.gridwidth = 1;
        panel.add(new JLabel("目标格式:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(formatBox, gc);
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 0;
        panel.add(zlibLabel, gc);
        gc.gridy++;
        panel.add(zlibSlider, gc);
        gc.gridy++;
        gc.gridwidth = 1;
        panel.add(new JLabel("压缩算法:"), gc);
        gc.gridx = 1;
        panel.add(modeBox, gc);
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        panel.add(modeHint, gc);
        gc.gridy++;
        gc.gridwidth = 1;
        panel.add(new JLabel("块缩放:"), gc);
        gc.gridx = 1;
        panel.add(scaleBox, gc);
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        panel.add(scaleHint, gc);
        gc.gridy++;
        panel.add(ditherCb, gc);
        gc.gridy++;
        panel.add(skipCb, gc);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "强力压缩设置",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        WzPngFormat format = (WzPngFormat) formatBox.getSelectedItem();
        int zlib = zlibSlider.getValue();
        WzPngZlibCompressMode zlibMode = (WzPngZlibCompressMode) modeBox.getSelectedItem();
        if (zlibMode == null) {
            zlibMode = WzPngZlibCompressMode.DEFAULT;
        }
        int scaleIdx = scaleBox.getSelectedIndex();
        int pngScale = Math.max(0, Math.min(2, scaleIdx));
        if (format != WzPngFormat.ARGB4444 && format != WzPngFormat.RGB565) {
            pngScale = 0;
        }

        return new StrongCompressOptions(
                format,
                zlib,
                zlibMode,
                pngScale,
                ditherCb.isSelected(),
                skipCb.isSelected()
        );
    }
}
