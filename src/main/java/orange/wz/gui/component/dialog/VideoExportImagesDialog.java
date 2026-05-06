package orange.wz.gui.component.dialog;

import com.formdev.flatlaf.util.SystemFileChooser;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import orange.wz.gui.video.VideoImageBitDepth;

/**
 * 导出 Canvas#Video 为图片序列或 GIF。
 */
public class VideoExportImagesDialog extends JDialog {

    public enum ImageExportFormat {
        PNG, JPG, GIF
    }

    @Getter
    public static final class Result {
        private final List<Integer> frameIndices;
        private final VideoImageBitDepth bitDepth;
        private final Path directory;
        private final ImageExportFormat format;

        public Result(List<Integer> frameIndices, VideoImageBitDepth bitDepth, Path directory, ImageExportFormat format) {
            this.frameIndices = frameIndices;
            this.bitDepth = bitDepth;
            this.directory = directory;
            this.format = format;
        }
    }

    private final int totalFrameCount;

    private final JRadioButton radioAll;
    private final JRadioButton radioOdd;
    private final JRadioButton radioEven;
    private final JPanel pngFrameRow;
    private final JLabel gifFrameSummaryLabel;
    private final JTextArea frameIndexArea;
    private final JPanel pngCustomPanel;
    private final JComboBox<ImageExportFormat> formatCombo;
    private final JComboBox<VideoImageBitDepth> bitDepthCombo;
    private final JTextField dirField;
    private Result result;

    public VideoExportImagesDialog(Window owner, int totalFrameCount) {
        super(owner, "导出图片集", ModalityType.APPLICATION_MODAL);
        this.totalFrameCount = totalFrameCount;

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        ButtonGroup frameGroup = new ButtonGroup();
        radioAll = new JRadioButton("全部帧", true);
        radioOdd = new JRadioButton("奇数帧");
        radioEven = new JRadioButton("偶数帧");
        frameGroup.add(radioAll);
        frameGroup.add(radioOdd);
        frameGroup.add(radioEven);

        pngFrameRow = new JPanel();
        pngFrameRow.setLayout(new BoxLayout(pngFrameRow, BoxLayout.X_AXIS));
        pngFrameRow.add(radioAll);
        pngFrameRow.add(Box.createHorizontalStrut(8));
        pngFrameRow.add(radioOdd);
        pngFrameRow.add(Box.createHorizontalStrut(8));
        pngFrameRow.add(radioEven);
        pngFrameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        gifFrameSummaryLabel = new JLabel();
        gifFrameSummaryLabel.setVisible(false);

        JPanel exportFrameValue = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        exportFrameValue.setBorder(new EmptyBorder(0, secondaryColumnLeadingAlignmentPad(), 0, 0));
        exportFrameValue.add(pngFrameRow);
        exportFrameValue.add(gifFrameSummaryLabel);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("导出帧数:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        form.add(exportFrameValue, gbc);

        frameIndexArea = new JTextArea(5, 42);
        frameIndexArea.setLineWrap(true);
        frameIndexArea.setWrapStyleWord(true);
        frameIndexArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, frameIndexArea.getFont().getSize()));
        JScrollPane frameScroll = new JScrollPane(frameIndexArea);
        pngCustomPanel = new JPanel(new BorderLayout());
        pngCustomPanel.add(frameScroll, BorderLayout.CENTER);

        JLabel frameIndexLabel = new JLabel("帧序号:");
        frameIndexLabel.setToolTipText("0 起始，逗号或空格分隔，可编辑");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        form.add(frameIndexLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(pngCustomPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("导出目录:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        dirField = new JTextField(24);
        JButton browse = new JButton("浏览…");
        browse.addActionListener(e -> pickDirectory());
        JPanel dirRow = new JPanel(new BorderLayout(4, 0));
        dirRow.add(dirField, BorderLayout.CENTER);
        dirRow.add(browse, BorderLayout.EAST);
        form.add(dirRow, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("图片封装格式:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        formatCombo = new JComboBox<>(ImageExportFormat.values());
        formatCombo.setSelectedItem(ImageExportFormat.PNG);
        formatCombo.addActionListener(e -> updateForFormat());
        form.add(formatCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        form.add(new JLabel("图片位深格式:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        bitDepthCombo = new JComboBox<>(VideoImageBitDepth.values());
        bitDepthCombo.setSelectedItem(VideoImageBitDepth.ARGB8888);
        form.add(bitDepthCombo, gbc);

        add(form, BorderLayout.CENTER);

        ItemListener radioFill = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                applyRadioToTextArea();
            }
        };
        radioAll.addItemListener(radioFill);
        radioOdd.addItemListener(radioFill);
        radioEven.addItemListener(radioFill);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("确定");
        JButton cancel = new JButton("取消");
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        buttons.add(ok);
        buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);

        fillTextAreaAll();
        updateForFormat();
        pack();
        setLocationRelativeTo(owner);
    }

    private void applyRadioToTextArea() {
        if (formatCombo.getSelectedItem() == ImageExportFormat.GIF) {
            return;
        }
        if (radioOdd.isSelected()) {
            fillTextAreaOdd();
        } else if (radioEven.isSelected()) {
            fillTextAreaEven();
        } else {
            fillTextAreaAll();
        }
    }

    private void fillTextAreaAll() {
        frameIndexArea.setText(buildCommaList(IntStream.range(0, totalFrameCount)));
    }

    private void fillTextAreaOdd() {
        frameIndexArea.setText(buildCommaList(IntStream.range(0, totalFrameCount).filter(i -> (i & 1) == 1)));
    }

    private void fillTextAreaEven() {
        frameIndexArea.setText(buildCommaList(IntStream.range(0, totalFrameCount).filter(i -> (i & 1) == 0)));
    }

    private static String buildCommaList(IntStream stream) {
        return stream.mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    private void updateForFormat() {
        boolean gif = formatCombo.getSelectedItem() == ImageExportFormat.GIF;
        pngFrameRow.setVisible(!gif);
        gifFrameSummaryLabel.setVisible(gif);
        pngCustomPanel.setVisible(!gif);
        if (gif) {
            gifFrameSummaryLabel.setText(totalFrameCount + " 帧（全部导出）");
        } else {
            applyRadioToTextArea();
        }
        revalidate();
        pack();
    }

    private void pickDirectory() {
        SystemFileChooser ch = new SystemFileChooser();
        ch.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        ch.setDialogTitle("选择导出目录");
        if (ch.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            File f = ch.getSelectedFile();
            if (f != null) {
                dirField.setText(f.getAbsolutePath());
            }
        }
    }

    private List<Integer> parseFrameIndicesFromTextArea() throws IllegalArgumentException {
        String raw = frameIndexArea.getText();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("帧序号不能为空");
        }
        List<Integer> out = new ArrayList<>();
        String[] parts = raw.split("[,，\\s;；]+");
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            int v;
            try {
                v = Integer.parseInt(p.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("无法解析的帧序号: " + p.trim());
            }
            if (v < 0 || v >= totalFrameCount) {
                throw new IllegalArgumentException("帧序号越界: " + v + "（有效范围 0～" + (totalFrameCount - 1) + "）");
            }
            out.add(v);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("未解析到任何有效帧序号");
        }
        return out;
    }

    /**
     * 第二列表格列内：文本框/下拉框相对控件边界有左侧留白，单选框默认更靠左；补足后与下方输入控件左缘对齐。
     */
    private static int secondaryColumnLeadingAlignmentPad() {
        JTextField tf = new JTextField();
        tf.updateUI();
        JRadioButton rb = new JRadioButton(".");
        rb.updateUI();
        JComboBox<ImageExportFormat> combo = new JComboBox<>(ImageExportFormat.values());
        combo.updateUI();
        Insets ti = tf.getInsets();
        Insets ci = combo.getInsets();
        Insets ri = rb.getInsets();
        int targetLead = Math.max(ti != null ? ti.left : 0, ci != null ? ci.left : 0);
        int radioLead = ri != null ? ri.left : 0;
        return Math.max(0, targetLead - radioLead);
    }

    private void onOk() {
        ImageExportFormat fmt = (ImageExportFormat) formatCombo.getSelectedItem();
        VideoImageBitDepth depth = (VideoImageBitDepth) bitDepthCombo.getSelectedItem();
        String dirText = dirField.getText().trim();
        Path dir = null;
        if (!dirText.isEmpty()) {
            dir = Path.of(dirText);
            if (!dir.toFile().isDirectory()) {
                JOptionPane.showMessageDialog(this, "导出目录不存在或不是文件夹", "无效目录", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (totalFrameCount <= 0) {
            JOptionPane.showMessageDialog(this, "没有可导出的帧", "无效", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Integer> indices;
        if (fmt == ImageExportFormat.GIF) {
            indices = new ArrayList<>(totalFrameCount);
            for (int i = 0; i < totalFrameCount; i++) {
                indices.add(i);
            }
        } else {
            try {
                indices = parseFrameIndicesFromTextArea();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "帧序号无效", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        result = new Result(indices, depth, dir, fmt);
        dispose();
    }

    public Result openAndGetResult() {
        setVisible(true);
        return result;
    }
}
