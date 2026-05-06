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
        JLabel modeHint = new JLabel("「极限体积」对每张图尝试多种 zlib 策略取最小（慢）；渐变图可试「滤波优先」");
        modeHint.setFont(modeHint.getFont().deriveFont(Font.PLAIN, modeHint.getFont().getSize() - 1f));

        JComboBox<StrongCompressQuantMode> quantBox = new JComboBox<>(StrongCompressQuantMode.values());
        quantBox.setSelectedItem(StrongCompressQuantMode.NONE);
        quantBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof StrongCompressQuantMode m) {
                    setText(m.displayLabel());
                }
                return this;
            }
        });
        JLabel quantHint = new JLabel(
                "libimagequant：解码→ARGB8888→调色板量化→按目标格式写回；随工程打包 org.pngquant JNI 与 resources/libimagequant 原生库，无需 vendor 子仓库");
        quantHint.setFont(quantHint.getFont().deriveFont(Font.PLAIN, quantHint.getFont().getSize() - 1f));

        JSpinner liqColorsSpinner = new JSpinner(new SpinnerNumberModel(256, 2, 256, 1));
        JSpinner liqSpeedSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));
        JSlider liqDitherSlider = new JSlider(0, 100, 100);
        liqDitherSlider.setMajorTickSpacing(25);
        liqDitherSlider.setPaintTicks(true);
        JLabel liqDitherLabel = new JLabel("LIQ 抖动: 100%");
        liqDitherSlider.addChangeListener(e ->
                liqDitherLabel.setText("LIQ 抖动: " + liqDitherSlider.getValue() + "%"));

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
        skipCb.setToolTipText(
                "仅当「目标格式与块缩放」与原始一致时才比较体积并可能还原；"
                        + "若目标为 ARGB8888 等而原为 DXT5/BC7，即使变大也会保留转换结果。");

        Runnable refreshRelatedControls = () -> {
            WzPngFormat f = (WzPngFormat) formatBox.getSelectedItem();
            StrongCompressQuantMode q = (StrongCompressQuantMode) quantBox.getSelectedItem();
            boolean liq = q == StrongCompressQuantMode.LIBIMAGEQUANT;
            liqColorsSpinner.setEnabled(liq);
            liqSpeedSpinner.setEnabled(liq);
            liqDitherSlider.setEnabled(liq);
            liqDitherLabel.setEnabled(liq);

            boolean enScale = f == WzPngFormat.ARGB4444 || f == WzPngFormat.RGB565;
            scaleBox.setEnabled(enScale);

            boolean manualFs = !liq && (f == WzPngFormat.ARGB4444 || f == WzPngFormat.RGB565);
            ditherCb.setEnabled(manualFs);
            if (!manualFs) {
                ditherCb.setSelected(false);
            }
        };
        formatBox.addActionListener(e -> refreshRelatedControls.run());
        quantBox.addActionListener(e -> refreshRelatedControls.run());
        refreshRelatedControls.run();

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
        panel.add(new JLabel("像素量化:"), gc);
        gc.gridx = 1;
        panel.add(quantBox, gc);
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        panel.add(quantHint, gc);
        gc.gridy++;
        gc.gridwidth = 1;
        panel.add(new JLabel("LIQ 最大色数:"), gc);
        gc.gridx = 1;
        panel.add(liqColorsSpinner, gc);
        gc.gridy++;
        gc.gridx = 0;
        panel.add(new JLabel("LIQ speed:"), gc);
        gc.gridx = 1;
        panel.add(liqSpeedSpinner, gc);
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        panel.add(liqDitherLabel, gc);
        gc.gridy++;
        panel.add(liqDitherSlider, gc);
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

        StrongCompressQuantMode quantModeSel = (StrongCompressQuantMode) quantBox.getSelectedItem();
        if (quantModeSel == StrongCompressQuantMode.LIBIMAGEQUANT && !LibimagequantStrongCompress.isRuntimeAvailable()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "未在 classpath 中找到 libimagequant 原生库资源（例如 libimagequant/libimagequant.dll）。\n"
                            + "请从 java-png-compress-util 的 src/main/resources/libimagequant 目录\n"
                            + "将对应平台的库文件复制到本项目的 src/main/resources/libimagequant/ 后重新打包。",
                    "缺少 libimagequant",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        WzPngFormat format = (WzPngFormat) formatBox.getSelectedItem();
        int zlib = zlibSlider.getValue();
        WzPngZlibCompressMode zlibMode = (WzPngZlibCompressMode) modeBox.getSelectedItem();
        if (zlibMode == null) {
            zlibMode = WzPngZlibCompressMode.DEFAULT;
        }
        StrongCompressQuantMode quantMode = quantModeSel != null ? quantModeSel : StrongCompressQuantMode.NONE;
        int liqColors = (Integer) liqColorsSpinner.getValue();
        int liqSpeed = (Integer) liqSpeedSpinner.getValue();
        float liqDither = liqDitherSlider.getValue() / 100f;

        int scaleIdx = scaleBox.getSelectedIndex();
        int pngScale = Math.max(0, Math.min(2, scaleIdx));
        if (format != WzPngFormat.ARGB4444 && format != WzPngFormat.RGB565) {
            pngScale = 0;
        }

        return new StrongCompressOptions(
                format,
                zlib,
                zlibMode,
                quantMode,
                liqColors,
                liqSpeed,
                liqDither,
                pngScale,
                ditherCb.isSelected(),
                skipCb.isSelected()
        );
    }
}
