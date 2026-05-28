package orange.wz.gui.component.dialog;

import orange.wz.gui.component.panel.ImagePanel;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzPngProperty;
import orange.wz.provider.properties.WzPngZlibCompressMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.Deflater;

public final class ImageCompareDialog extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(ImageCompareDialog.class);
    private static final String PREF_AUTO_REPAIR_ON_REPLACE = "autoRepairOnReplace";

    private JList<String> stringList;
    private DefaultListModel<String> listModel;
    private final List<String> collections = new ArrayList<>();
    private ImagePanel imagePanel1;
    private ImagePanel imagePanel2;
    private JLabel widthLabel1;
    private JLabel widthLabel2;
    private JLabel heightLabel1;
    private JLabel heightLabel2;
    private JLabel formatLabel1;
    private JLabel formatLabel2;
    private JLabel scaleLabel1;
    private JLabel scaleLabel2;

    private JCheckBox includeChildren;
    private JCheckBox autoRepairOnReplace;
    private JLabel statusLabel;
    private final Map<String, WzCanvasProperty> toMap = new HashMap<>();
    private final Map<String, WzCanvasProperty> fromMap = new HashMap<>();
    private WzCanvasProperty curTo;
    private WzCanvasProperty curFrom;

    /** 按扫描顺序保存的全部路径，用于筛选后恢复列表 */
    private final List<String> fullPathsInOrder = new ArrayList<>();
    /** 已替换/修补过的路径（按路径记，避免筛选后列表索引变化导致标记错乱） */
    private final Set<String> changedPaths = new HashSet<>();
    private boolean sizeMismatchFilterActive;
    private boolean imageDiffFilterActive;
    private JButton sizeMismatchFilterBtn;
    private JButton imageDiffFilterBtn;

    public ImageCompareDialog(Frame owner) {
        super("图片对比");
        if (owner != null) {
            setIconImage(owner.getIconImage());
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        setSize(1000, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        bindKeys();

        // --- 添加窗口关闭监听 ---
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onDialogClosing();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                onDialogClosing();
            }
        });

        setVisible(true);
    }

    private JComponent buildMainPanel() {
        JScrollPane left = buildStringListPanel();
        JPanel center = buildImageInfoPanel("原图", true);
        JPanel right = buildImageInfoPanel("替换", false);

        // 中 + 右 先合成一个面板
        JPanel rightGroup = new JPanel(new GridLayout(1, 2, 10, 10));
        rightGroup.add(center);
        rightGroup.add(right);

        // 左 + (中右) 用 JSplitPane 控制比例
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightGroup);
        split.setResizeWeight(0.2);           // 左侧占 20%
        split.setDividerLocation(220);        // 初始宽度 220px
        split.setOneTouchExpandable(false);

        return split;
    }

    /**
     * 左侧字符串列表
     */
    private JScrollPane buildStringListPanel() {
        listModel = new DefaultListModel<>();
        stringList = new JList<>(listModel);
        stringList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 超出宽度用 ... 显示
        stringList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);

                // 默认文本颜色
                lbl.setForeground(Color.BLACK);

                // 如果被标记了特殊颜色
                if (value != null && changedPaths.contains(value.toString())) {
                    lbl.setForeground(Color.MAGENTA);
                }

                // 文本省略显示
                lbl.setToolTipText(value.toString());
                lbl.setText(ellipsis(value.toString(), list.getWidth() - 20, lbl.getFontMetrics(lbl.getFont())));

                return lbl;
            }
        });

        stringList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = stringList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String sel = stringList.getModel().getElementAt(index);
                        onStringSelected(sel);
                    }
                }
            }
        });

        stringList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // 只触发一次
                String sel = stringList.getSelectedValue();
                if (sel != null) {
                    onStringSelected(sel);
                }
            }
        });


        // 让 JList 也支持快捷键
        InputMap im = stringList.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = stringList.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "replace");
        am.put("replace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                replaceImage();
            }
        });


        return new JScrollPane(stringList);
    }

    private static JSlider createPreviewZoomSlider(ImagePanel imagePanel) {
        JSlider slider = new JSlider(10, 300, 100);
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setToolTipText("拖动调节预览图片显示大小（10%–300%）");
        slider.addChangeListener(e -> imagePanel.setZoomFactor(slider.getValue() / 100.0));
        return slider;
    }

    /**
     * 中 / 右 图片 + 参数
     */
    private JPanel buildImageInfoPanel(String title, boolean first) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        ImagePanel imageLabel = new ImagePanel();
        imageLabel.setPreferredSize(new Dimension(250, 250));
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JScrollPane imageScroll = new JScrollPane(imageLabel);
        imageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        imageScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel imageColumn = new JPanel(new BorderLayout(5, 5));
        imageColumn.add(imageScroll, BorderLayout.CENTER);
        imageColumn.add(createPreviewZoomSlider(imageLabel), BorderLayout.SOUTH);

        // 参数信息（横向排列）
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        info.setBorder(new EmptyBorder(5, 8, 5, 8));
        JLabel widthLabel = new JLabel("Width: ");
        JLabel heightLabel = new JLabel("Height: ");
        JLabel formatLabel = new JLabel("Format: ");
        JLabel scaleLabel = new JLabel("Scale: ");
        info.add(widthLabel);
        info.add(heightLabel);
        info.add(formatLabel);
        info.add(scaleLabel);

        panel.add(imageColumn, BorderLayout.CENTER);
        panel.add(info, BorderLayout.SOUTH);

        if (first) {
            imagePanel1 = imageLabel;
            widthLabel1 = widthLabel;
            heightLabel1 = heightLabel;
            formatLabel1 = formatLabel;
            scaleLabel1 = scaleLabel;
        } else {
            imagePanel2 = imageLabel;
            widthLabel2 = widthLabel;
            heightLabel2 = heightLabel;
            formatLabel2 = formatLabel;
            scaleLabel2 = scaleLabel;
        }

        return panel;
    }

    /**
     * 底部按钮
     */
    private JPanel buildBottomPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        // 按钮区
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 8));
        includeChildren = new JCheckBox("包括Origin等子节点");
        includeChildren.setSelected(true);
        JButton repairSizeBtn = new JButton("图片尺寸修补");
        sizeMismatchFilterBtn = new JButton("大小差异筛选");
        imageDiffFilterBtn = new JButton("图片差异筛选");
        JButton replaceBtn = new JButton("替换 (空格键)");
        autoRepairOnReplace = new JCheckBox("自动修补");
        autoRepairOnReplace.setSelected(PREFS.getBoolean(PREF_AUTO_REPAIR_ON_REPLACE, true));
        autoRepairOnReplace.addItemListener(e ->
                PREFS.putBoolean(PREF_AUTO_REPAIR_ON_REPLACE, autoRepairOnReplace.isSelected()));

        repairSizeBtn.addActionListener(e -> repairImageSizeToReference());
        sizeMismatchFilterBtn.addActionListener(e -> toggleSizeMismatchFilter());
        imageDiffFilterBtn.addActionListener(e -> toggleImageDiffFilter());
        replaceBtn.addActionListener(e -> replaceImage());

        buttons.add(includeChildren);
        buttons.add(repairSizeBtn);
        buttons.add(sizeMismatchFilterBtn);
        buttons.add(imageDiffFilterBtn);
        buttons.add(replaceBtn);
        buttons.add(autoRepairOnReplace);

        // 状态栏
        statusLabel = new JLabel("扫描图片中...");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        wrapper.add(buttons, BorderLayout.NORTH);
        wrapper.add(statusLabel, BorderLayout.SOUTH);

        return wrapper;
    }


    /**
     * 绑定快捷键
     */
    private void bindKeys() {
        JRootPane root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "replace");
        am.put("replace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                replaceImage();
            }
        });
    }

    private void replaceImage() {
        if (curTo == null || curFrom == null) {
            return;
        }

        BufferedImage image;

        if (autoRepairOnReplace.isSelected()) {
            BufferedImage src = curFrom.getPngImage(false);
            if (src == null) {
                JOptionPane.showMessageDialog(this, "替换图解码失败，无法自动修补。", "替换", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int tw = curTo.getWidth();
            int th = curTo.getHeight();
            if (tw <= 0 || th <= 0) {
                JOptionPane.showMessageDialog(this, "原图尺寸无效，无法自动修补。", "替换", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int sw = src.getWidth();
            int sh = src.getHeight();
            if (sw <= 0 || sh <= 0) {
                JOptionPane.showMessageDialog(this, "替换图尺寸无效，无法自动修补。", "替换", JOptionPane.ERROR_MESSAGE);
                return;
            }
            WzPngFormat leftFormat = curTo.getFormat();
            int leftScale = curTo.getScale();
            image = fitImageToCanvas(src, tw, th, CanvasFitMode.FIT_INSIDE);
            curTo.setPng(image, leftFormat, leftScale, Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        } else {
            image = curFrom.getPngImage(false);
            if (image == null) {
                JOptionPane.showMessageDialog(this, "替换图解码失败。", "替换", JOptionPane.WARNING_MESSAGE);
                return;
            }
            curTo.setPng(image, curFrom.getFormat(), curFrom.getScale(),
                    Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        }

        curTo.clearImage();
        imagePanel1.setImage(image);
        refreshLeftImageInfo();

        if (includeChildren.isSelected()) {
            List<WzImageProperty> children = new ArrayList<>();
            curFrom.getChildren().forEach(child -> children.add(child.deepClone(curTo)));
            curTo.replaceChildrenList(children);
        }

        String path = stringList.getSelectedValue();
        if (path != null) {
            changedPaths.add(path);
        }
        int index = stringList.getSelectedIndex();
        if (index >= 0 && index + 1 < listModel.getSize()) {
            stringList.setSelectedIndex(index + 1);
        }

        setStatus("已修改 " + changedPaths.size() + " / " + getTotalPathCount() + " 条");
    }

    /**
     * 将左侧「原图」按右侧「替换」图的画布尺寸调整：不拉伸变形。
     * 左宽、左高均不大于右：原图居中置于透明画布（不缩放）。
     * 否则：先缩放到与右图同宽，高度超出则上下对称居中裁剪，不足则上下对称补透明。
     */
    private void repairImageSizeToReference() {
        if (curTo == null || curFrom == null) {
            return;
        }
        BufferedImage src = curTo.getPngImage(false);
        if (src == null) {
            JOptionPane.showMessageDialog(this, "原图解码失败，无法修补。", "图片尺寸修补", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int tw = curFrom.getWidth();
        int th = curFrom.getHeight();
        if (tw <= 0 || th <= 0) {
            JOptionPane.showMessageDialog(this, "替换图尺寸无效。", "图片尺寸修补", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            JOptionPane.showMessageDialog(this, "原图尺寸无效。", "图片尺寸修补", JOptionPane.ERROR_MESSAGE);
            return;
        }

        BufferedImage out = fitImageToCanvas(src, tw, th, CanvasFitMode.FIT_TARGET_WIDTH);

        WzPngFormat leftFormat = curTo.getFormat();
        int leftScale = curTo.getScale();
        curTo.setPng(out, leftFormat, leftScale, Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        curTo.clearImage();
        imagePanel1.setImage(out);
        refreshLeftImageInfo();

        String path = stringList.getSelectedValue();
        if (path != null) {
            changedPaths.add(path);
            stringList.repaint();
        }
        int index = stringList.getSelectedIndex();
        if (index >= 0 && index + 1 < listModel.getSize()) {
            stringList.setSelectedIndex(index + 1);
        }
        setStatus("已修改 " + changedPaths.size() + " / " + getTotalPathCount() + " 条");
    }

    private enum CanvasFitMode {
        /** 缩放到目标宽度，高度超出则居中裁剪，不足则补透明 */
        FIT_TARGET_WIDTH,
        /** 等比缩小至完全落入目标画布，不裁剪，居中补透明 */
        FIT_INSIDE
    }

    /**
     * 将源图适配到目标画布尺寸，保持比例、不拉伸变形。
     */
    private static BufferedImage fitImageToCanvas(BufferedImage src, int tw, int th, CanvasFitMode mode) {
        int sw = src.getWidth();
        int sh = src.getHeight();

        if (sw <= tw && sh <= th) {
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setComposite(AlphaComposite.Src);
                g.drawImage(src, (tw - sw) / 2, (th - sh) / 2, null);
            } finally {
                g.dispose();
            }
            return out;
        }

        if (mode == CanvasFitMode.FIT_INSIDE) {
            double ratio = Math.min((double) tw / sw, (double) th / sh);
            int newW = Math.max(1, (int) Math.round(sw * ratio));
            int newH = Math.max(1, (int) Math.round(sh * ratio));
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setComposite(AlphaComposite.Src);
                int x = (tw - newW) / 2;
                int y = (th - newH) / 2;
                g.drawImage(src, x, y, newW, newH, null);
            } finally {
                g.dispose();
            }
            return out;
        }

        BufferedImage scaled = scaleToWidthArgb(src, tw);
        int scaledH = scaled.getHeight();
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setComposite(AlphaComposite.Src);
            if (scaledH > th) {
                int cropY = (scaledH - th) / 2;
                g.drawImage(scaled, 0, 0, tw, th, 0, cropY, tw, cropY + th, null);
            } else {
                int y = (th - scaledH) / 2;
                g.drawImage(scaled, 0, y, null);
            }
        } finally {
            g.dispose();
        }
        scaled.flush();
        return out;
    }

    private void refreshLeftImageInfo() {
        if (curTo != null && curFrom != null) {
            updateImageInfoLabels(curTo, curFrom);
        }
    }

    private void updateImageInfoLabels(WzCanvasProperty left, WzCanvasProperty right) {
        widthLabel1.setText("Width: " + left.getWidth());
        heightLabel1.setText("Height: " + left.getHeight());
        formatLabel1.setText("Format: " + left.getFormat());
        scaleLabel1.setText("Scale: " + left.getScale());

        widthLabel2.setText("Width: " + right.getWidth());
        heightLabel2.setText("Height: " + right.getHeight());
        formatLabel2.setText("Format: " + right.getFormat());
        scaleLabel2.setText("Scale: " + right.getScale());

        setOriginalInfoDiffColor(widthLabel1, left.getWidth() != right.getWidth());
        setOriginalInfoDiffColor(heightLabel1, left.getHeight() != right.getHeight());
        setOriginalInfoDiffColor(formatLabel1, left.getFormat() != right.getFormat());
        setOriginalInfoDiffColor(scaleLabel1, left.getScale() != right.getScale());
    }

    private static void setOriginalInfoDiffColor(JLabel label, boolean differs) {
        label.setForeground(differs ? Color.RED : UIManager.getColor("Label.foreground"));
    }

    private static BufferedImage scaleToWidthArgb(BufferedImage src, int targetW) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int targetH = (int) Math.round((double) sh * targetW / (double) sw);
        if (targetH < 1) {
            targetH = 1;
        }
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /**
     * 文本省略显示
     */
    private String ellipsis(String text, int maxWidth, FontMetrics fm) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String dots = "...";
        int w = fm.stringWidth(dots);
        int i = text.length() - 1;
        while (i > 0 && fm.stringWidth(text.substring(0, i)) + w > maxWidth) {
            i--;
        }
        return text.substring(0, i) + dots;
    }

    private void onStringSelected(String value) {
        curTo = toMap.get(value);
        curFrom = fromMap.get(value);
        imagePanel1.setImage(curTo.getPngImage(false));
        imagePanel2.setImage(curFrom.getPngImage(false));
        updateImageInfoLabels(curTo, curFrom);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    private int getTotalPathCount() {
        if (!fullPathsInOrder.isEmpty()) {
            return fullPathsInOrder.size();
        }
        return toMap.size();
    }

    private boolean pathHasSizeMismatch(String path) {
        WzCanvasProperty left = toMap.get(path);
        WzCanvasProperty right = fromMap.get(path);
        return left != null && right != null
                && (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight());
    }

    private boolean pathHasImageDifference(String path) {
        WzCanvasProperty left = toMap.get(path);
        WzCanvasProperty right = fromMap.get(path);
        if (left == null || right == null) {
            return false;
        }
        if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
            return true;
        }
        if (left.getFormat() != right.getFormat() || left.getScale() != right.getScale()) {
            return true;
        }
        int leftLen = left.getCompressedPngStorageLength();
        int rightLen = right.getCompressedPngStorageLength();
        if (leftLen != rightLen) {
            return true;
        }
        if (leftLen == 0) {
            return false;
        }
        WzPngProperty.CompressedPngData leftData = left.exportCompressedPngData();
        WzPngProperty.CompressedPngData rightData = right.exportCompressedPngData();
        byte[] leftBytes = leftData != null ? leftData.getCompressedBytes() : null;
        byte[] rightBytes = rightData != null ? rightData.getCompressedBytes() : null;
        if (leftBytes == null || rightBytes == null) {
            return leftBytes != rightBytes;
        }
        return !Arrays.equals(leftBytes, rightBytes);
    }

    private boolean pathPassesActiveFilter(String path) {
        if (sizeMismatchFilterActive) {
            return pathHasSizeMismatch(path);
        }
        if (imageDiffFilterActive) {
            return pathHasImageDifference(path);
        }
        return true;
    }

    private List<String> getFullPathSourceOrder() {
        if (!fullPathsInOrder.isEmpty()) {
            return fullPathsInOrder;
        }
        return Collections.list(listModel.elements());
    }

    private void deactivateAllFilters() {
        sizeMismatchFilterActive = false;
        imageDiffFilterActive = false;
        sizeMismatchFilterBtn.setText("大小差异筛选");
        imageDiffFilterBtn.setText("图片差异筛选");
    }

    private void applyFilteredList(List<String> filtered, int totalCount, String statusPrefix) {
        listModel.clear();
        listModel.addAll(filtered);
        if (!listModel.isEmpty()) {
            stringList.setSelectedIndex(0);
            stringList.ensureIndexIsVisible(0);
            onStringSelected(listModel.get(0));
        } else {
            curTo = null;
            curFrom = null;
            imagePanel1.setImage(null);
            imagePanel2.setImage(null);
        }
        setStatus(statusPrefix + filtered.size() + " / " + totalCount
                + " 条（已修改 " + changedPaths.size() + " 条）");
    }

    private void restoreFullPathList() {
        listModel.clear();
        listModel.addAll(fullPathsInOrder.isEmpty()
                ? new ArrayList<>(toMap.keySet())
                : fullPathsInOrder);
        deactivateAllFilters();
        if (!listModel.isEmpty()) {
            stringList.setSelectedIndex(0);
            stringList.ensureIndexIsVisible(0);
            onStringSelected(listModel.get(0));
        }
        setStatus("已修改 " + changedPaths.size() + " / " + getTotalPathCount() + " 条");
    }

    /** 将 collections 刷入左侧列表；若正在筛选，只追加符合当前筛选条件的项 */
    private void flushCollectionsToListModel() {
        if (collections.isEmpty()) {
            return;
        }
        if (sizeMismatchFilterActive || imageDiffFilterActive) {
            for (String p : collections) {
                if (pathPassesActiveFilter(p)) {
                    listModel.addElement(p);
                }
            }
        } else {
            listModel.addAll(collections);
        }
        collections.clear();
    }

    private void toggleSizeMismatchFilter() {
        if (sizeMismatchFilterActive) {
            restoreFullPathList();
            return;
        }
        if (fullPathsInOrder.isEmpty() && listModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可筛选的路径。", "大小差异筛选", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        imageDiffFilterActive = false;
        imageDiffFilterBtn.setText("图片差异筛选");

        List<String> sourceOrder = getFullPathSourceOrder();
        List<String> mismatched = new ArrayList<>();
        for (String path : sourceOrder) {
            if (pathHasSizeMismatch(path)) {
                mismatched.add(path);
            }
        }
        sizeMismatchFilterActive = true;
        sizeMismatchFilterBtn.setText("显示全部列表");
        applyFilteredList(mismatched, sourceOrder.size(), "大小差异 ");
    }

    private void toggleImageDiffFilter() {
        if (imageDiffFilterActive) {
            restoreFullPathList();
            return;
        }
        if (fullPathsInOrder.isEmpty() && listModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可筛选的路径。", "图片差异筛选", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        sizeMismatchFilterActive = false;
        sizeMismatchFilterBtn.setText("大小差异筛选");

        List<String> sourceOrder = getFullPathSourceOrder();
        List<String> diff = new ArrayList<>();
        for (String path : sourceOrder) {
            if (pathHasImageDifference(path)) {
                diff.add(path);
            }
        }
        imageDiffFilterActive = true;
        imageDiffFilterBtn.setText("显示全部列表");
        applyFilteredList(diff, sourceOrder.size(), "图片差异 ");
    }

    public synchronized void addCompare(WzCanvasProperty to, WzCanvasProperty from) {
        String path = to.getPath();
        // listModel.addElement(path);
        collections.add(path);
        if (!toMap.containsKey(path)) {
            fullPathsInOrder.add(path);
        }
        toMap.put(path, to);
        fromMap.put(path, from);
        setStatus("已修改 " + changedPaths.size() + " / " + getTotalPathCount() + " 条");

        if (collections.size() > 25) {
            boolean selFirst = listModel.getSize() == 0;
            flushCollectionsToListModel();
            if (selFirst && !listModel.isEmpty()) {
                stringList.setSelectedIndex(0);
                stringList.ensureIndexIsVisible(0);
                onStringSelected(listModel.get(0));
            }
        }
    }

    public void completeScan() {
        flushCollectionsToListModel();
        if (listModel.getSize() == 0) {
            if ((sizeMismatchFilterActive || imageDiffFilterActive) && !toMap.isEmpty()) {
                String hint = sizeMismatchFilterActive ? "大小差异筛选下无尺寸不一致项" : "图片差异筛选下无内容差异项";
                setStatus("扫描完毕：" + hint + "，可点「显示全部列表」。");
            } else {
                setStatus("扫描完毕，找不到数据。");
            }
        } else {
            setStatus("已修改 " + changedPaths.size() + " / " + getTotalPathCount() + " 条");
        }
    }

    /**
     * 在对话框关闭时清理数据
     */
    private void onDialogClosing() {
        listModel.clear();
        toMap.clear();
        fromMap.clear();
        fullPathsInOrder.clear();
        changedPaths.clear();
        sizeMismatchFilterActive = false;
        imageDiffFilterActive = false;
        if (sizeMismatchFilterBtn != null) {
            sizeMismatchFilterBtn.setText("大小差异筛选");
        }
        if (imageDiffFilterBtn != null) {
            imageDiffFilterBtn.setText("图片差异筛选");
        }
        curTo = null;
        curFrom = null;
    }

}
