package orange.wz.gui.component.dialog;

import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.component.panel.ImagePanel;
import orange.wz.gui.utils.CanvasOriginCache;
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

    private static final String BASE_TITLE = "图片对比";

    private static final Preferences PREFS = Preferences.userNodeForPackage(ImageCompareDialog.class);
    private static final String PREF_AUTO_REPAIR_ON_REPLACE = "autoRepairOnReplace";
    private static final String PREF_WINDOW_WIDTH = "windowWidth";
    private static final String PREF_WINDOW_HEIGHT = "windowHeight";
    private static final String PREF_WINDOW_X = "windowX";
    private static final String PREF_WINDOW_Y = "windowY";
    private static final String PREF_MAIN_SPLIT_DIVIDER = "mainSplitDivider";
    private static final String PREF_PREVIEW_ZOOM_LEFT = "previewZoomLeft";
    private static final String PREF_PREVIEW_ZOOM_RIGHT = "previewZoomRight";

    private static final int DEFAULT_WINDOW_WIDTH = 1000;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final int DEFAULT_MAIN_SPLIT_DIVIDER = 220;
    private static final int DEFAULT_PREVIEW_ZOOM = 100;
    private static final int MIN_WINDOW_WIDTH = 640;
    private static final int MIN_WINDOW_HEIGHT = 400;

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
    private JLabel originXLabel1;
    private JLabel originYLabel1;
    private JLabel originXLabel2;
    private JLabel originYLabel2;

    private JCheckBox includeChildren;
    private JCheckBox replaceOrigin;
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
    private boolean sameSizeFilterActive;
    private boolean sizeMismatchFilterActive;
    private boolean originMismatchFilterActive;
    private boolean imageDiffFilterActive;
    private JButton sameSizeFilterBtn;
    private JButton sizeMismatchFilterBtn;
    private JButton originMismatchFilterBtn;
    private JButton imageDiffFilterBtn;
    private JButton batchSameSizeReplaceBtn;
    private JButton repairSizeBtn;
    private JButton replaceBtn;
    private JProgressBar batchProgressBar;
    private boolean batchSameSizeReplaceRunning;

    private boolean originCacheReady;

    private final EditPane toEditPane;
    private final EditPane fromEditPane;
    private final CanvasOriginCache toOriginCache;
    private final CanvasOriginCache fromOriginCache;

    private JSplitPane mainSplit;
    private JSlider previewZoomSlider1;
    private JSlider previewZoomSlider2;
    private boolean layoutRestored;
    private boolean dialogClosed;
    private javax.swing.Timer saveLayoutTimer;

    public ImageCompareDialog(Frame owner, EditPane toEditPane, EditPane fromEditPane) {
        super(BASE_TITLE);
        this.toEditPane = toEditPane;
        this.fromEditPane = fromEditPane;
        this.toOriginCache = new CanvasOriginCache(toEditPane);
        this.fromOriginCache = new CanvasOriginCache(fromEditPane);
        if (owner != null) {
            setIconImage(owner.getIconImage());
        }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(true);
        restoreWindowBounds(owner);
        setLayout(new BorderLayout(10, 10));

        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        bindKeys();
        bindLayoutPersistence();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                restoreInternalLayout();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                requestDialogClose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                onDialogClosed();
            }
        });

        setVisible(true);
    }

    private void restoreWindowBounds(Frame owner) {
        int width = Math.max(MIN_WINDOW_WIDTH, PREFS.getInt(PREF_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH));
        int height = Math.max(MIN_WINDOW_HEIGHT, PREFS.getInt(PREF_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT));
        setSize(width, height);
        if (PREFS.getBoolean("windowLocationSaved", false)) {
            setLocation(PREFS.getInt(PREF_WINDOW_X, 0), PREFS.getInt(PREF_WINDOW_Y, 0));
        } else if (owner != null) {
            setLocationRelativeTo(owner);
        }
    }

    private void restoreInternalLayout() {
        if (layoutRestored || mainSplit == null) {
            return;
        }
        layoutRestored = true;
        int divider = PREFS.getInt(PREF_MAIN_SPLIT_DIVIDER, DEFAULT_MAIN_SPLIT_DIVIDER);
        mainSplit.setDividerLocation(Math.max(120, divider));
        applyPreviewZoom(previewZoomSlider1, imagePanel1, PREFS.getInt(PREF_PREVIEW_ZOOM_LEFT, DEFAULT_PREVIEW_ZOOM));
        applyPreviewZoom(previewZoomSlider2, imagePanel2, PREFS.getInt(PREF_PREVIEW_ZOOM_RIGHT, DEFAULT_PREVIEW_ZOOM));
    }

    private static void applyPreviewZoom(JSlider slider, ImagePanel panel, int zoom) {
        if (slider == null || panel == null) {
            return;
        }
        int clamped = Math.max(10, Math.min(300, zoom));
        slider.setValue(clamped);
        panel.setZoomFactor(clamped / 100.0);
    }

    private void bindLayoutPersistence() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                scheduleSaveLayout();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                scheduleSaveLayout();
            }
        });
        if (mainSplit != null) {
            mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> scheduleSaveLayout());
        }
    }

    private void scheduleSaveLayout() {
        if (saveLayoutTimer == null) {
            saveLayoutTimer = new javax.swing.Timer(250, e -> saveLayoutPreferences());
            saveLayoutTimer.setRepeats(false);
        }
        saveLayoutTimer.restart();
    }

    private void saveLayoutPreferences() {
        if (!isShowing()) {
            return;
        }
        PREFS.putInt(PREF_WINDOW_WIDTH, getWidth());
        PREFS.putInt(PREF_WINDOW_HEIGHT, getHeight());
        PREFS.putInt(PREF_WINDOW_X, getX());
        PREFS.putInt(PREF_WINDOW_Y, getY());
        PREFS.putBoolean("windowLocationSaved", true);
        if (mainSplit != null) {
            PREFS.putInt(PREF_MAIN_SPLIT_DIVIDER, mainSplit.getDividerLocation());
        }
        if (previewZoomSlider1 != null) {
            PREFS.putInt(PREF_PREVIEW_ZOOM_LEFT, previewZoomSlider1.getValue());
        }
        if (previewZoomSlider2 != null) {
            PREFS.putInt(PREF_PREVIEW_ZOOM_RIGHT, previewZoomSlider2.getValue());
        }
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
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightGroup);
        mainSplit.setResizeWeight(0.2);
        mainSplit.setDividerLocation(PREFS.getInt(PREF_MAIN_SPLIT_DIVIDER, DEFAULT_MAIN_SPLIT_DIVIDER));
        mainSplit.setOneTouchExpandable(false);

        return mainSplit;
    }

    /**
     * 左侧字符串列表
     */
    private JScrollPane buildStringListPanel() {
        listModel = new DefaultListModel<>();
        stringList = new JList<>(listModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return false;
            }
        };
        stringList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        stringList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                String text = value != null ? value.toString() : "";
                lbl.setText(text);
                lbl.setToolTipText(text);
                if (value != null && changedPaths.contains(text)) {
                    lbl.setForeground(isSelected ? new Color(255, 180, 255) : Color.MAGENTA);
                } else if (!isSelected) {
                    lbl.setForeground(Color.BLACK);
                }
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

            @Override
            public void mousePressed(MouseEvent e) {
                showListContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showListContextMenu(e);
            }
        });

        stringList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                List<String> selected = stringList.getSelectedValuesList();
                if (!selected.isEmpty()) {
                    onStringSelected(selected.get(0));
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


        return new JScrollPane(stringList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    private JSlider createPreviewZoomSlider(ImagePanel imagePanel, boolean first) {
        int initialZoom = first
                ? PREFS.getInt(PREF_PREVIEW_ZOOM_LEFT, DEFAULT_PREVIEW_ZOOM)
                : PREFS.getInt(PREF_PREVIEW_ZOOM_RIGHT, DEFAULT_PREVIEW_ZOOM);
        JSlider slider = new JSlider(10, 300, Math.max(10, Math.min(300, initialZoom)));
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setToolTipText("拖动调节预览图片显示大小（10%–300%）");
        slider.addChangeListener(e -> {
            imagePanel.setZoomFactor(slider.getValue() / 100.0);
            if (!slider.getValueIsAdjusting()) {
                scheduleSaveLayout();
            }
        });
        if (first) {
            previewZoomSlider1 = slider;
            imagePanel.setZoomFactor(slider.getValue() / 100.0);
        } else {
            previewZoomSlider2 = slider;
            imagePanel.setZoomFactor(slider.getValue() / 100.0);
        }
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
        imageColumn.add(createPreviewZoomSlider(imageLabel, first), BorderLayout.SOUTH);

        // 参数信息（横向排列）
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        info.setBorder(new EmptyBorder(5, 8, 5, 8));
        JLabel widthLabel = new JLabel("Width: ");
        JLabel heightLabel = new JLabel("Height: ");
        JLabel formatLabel = new JLabel("Format: ");
        JLabel scaleLabel = new JLabel("Scale: ");
        JLabel originXLabel = new JLabel("Origin X: ");
        JLabel originYLabel = new JLabel("Origin Y: ");
        info.add(widthLabel);
        info.add(heightLabel);
        info.add(formatLabel);
        info.add(scaleLabel);
        info.add(originXLabel);
        info.add(originYLabel);

        panel.add(imageColumn, BorderLayout.CENTER);
        panel.add(info, BorderLayout.SOUTH);

        if (first) {
            imagePanel1 = imageLabel;
            widthLabel1 = widthLabel;
            heightLabel1 = heightLabel;
            formatLabel1 = formatLabel;
            scaleLabel1 = scaleLabel;
            originXLabel1 = originXLabel;
            originYLabel1 = originYLabel;
        } else {
            imagePanel2 = imageLabel;
            widthLabel2 = widthLabel;
            heightLabel2 = heightLabel;
            formatLabel2 = formatLabel;
            scaleLabel2 = scaleLabel;
            originXLabel2 = originXLabel;
            originYLabel2 = originYLabel;
        }

        return panel;
    }

    /**
     * 底部按钮
     */
    private JPanel buildBottomPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        includeChildren = new JCheckBox("包括Origin等子节点");
        includeChildren.setSelected(true);
        replaceOrigin = new JCheckBox("替换Origin描点");
        replaceOrigin.setSelected(true);
        repairSizeBtn = new JButton("图片尺寸修补");
        sameSizeFilterBtn = new JButton("同尺寸筛选");
        sizeMismatchFilterBtn = new JButton("不同尺寸筛选");
        originMismatchFilterBtn = new JButton("描点差异筛选");
        imageDiffFilterBtn = new JButton("图片差异筛选");
        batchSameSizeReplaceBtn = new JButton("同尺寸批量替换");
        replaceBtn = new JButton("替换 (空格键)");
        autoRepairOnReplace = new JCheckBox("自动修补");
        autoRepairOnReplace.setSelected(PREFS.getBoolean(PREF_AUTO_REPAIR_ON_REPLACE, true));
        autoRepairOnReplace.addItemListener(e ->
                PREFS.putBoolean(PREF_AUTO_REPAIR_ON_REPLACE, autoRepairOnReplace.isSelected()));

        repairSizeBtn.addActionListener(e -> repairImageSizeToReference());
        sameSizeFilterBtn.addActionListener(e -> toggleSameSizeFilter());
        sizeMismatchFilterBtn.addActionListener(e -> toggleSizeMismatchFilter());
        originMismatchFilterBtn.addActionListener(e -> toggleOriginMismatchFilter());
        imageDiffFilterBtn.addActionListener(e -> toggleImageDiffFilter());
        batchSameSizeReplaceBtn.addActionListener(e -> batchReplaceSameSize());
        replaceBtn.addActionListener(e -> replaceImage());

        buttons.add(includeChildren);
        buttons.add(replaceOrigin);
        buttons.add(repairSizeBtn);
        buttons.add(sameSizeFilterBtn);
        buttons.add(sizeMismatchFilterBtn);
        buttons.add(originMismatchFilterBtn);
        buttons.add(imageDiffFilterBtn);
        buttons.add(batchSameSizeReplaceBtn);
        buttons.add(replaceBtn);
        buttons.add(autoRepairOnReplace);

        // 状态栏
        statusLabel = new JLabel("扫描图片中...");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        batchProgressBar = new JProgressBar(0, 100);
        batchProgressBar.setStringPainted(true);
        batchProgressBar.setString("待执行");
        batchProgressBar.setVisible(false);
        batchProgressBar.setPreferredSize(new Dimension(260, 20));

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(batchProgressBar, BorderLayout.EAST);

        wrapper.add(buttons, BorderLayout.NORTH);
        wrapper.add(statusPanel, BorderLayout.SOUTH);

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
        if (batchSameSizeReplaceRunning) {
            return;
        }
        List<String> selected = stringList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        if (selected.size() > 1) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定替换选中的 " + selected.size() + " 张图片？",
                    "批量替换", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            for (String path : selected) {
                replaceSinglePath(path, false);
            }
            refreshListKeepingFilter();
            return;
        }
        replaceSinglePath(selected.get(0), true);
    }

    private void replaceSinglePath(String path, boolean advanceAfter) {
        replaceSinglePath(path, advanceAfter, true, true);
    }

    private boolean replaceSinglePath(String path, boolean advanceAfter, boolean refreshPreview, boolean updateStats) {
        return replaceSinglePath(path, advanceAfter, refreshPreview, updateStats,
                new ReplaceOptions(autoRepairOnReplace.isSelected(), includeChildren.isSelected(), replaceOrigin.isSelected()),
                true);
    }

    private boolean replaceSinglePath(String path, boolean advanceAfter, boolean refreshPreview,
                                      boolean updateStats, ReplaceOptions options, boolean markChanged) {
        WzCanvasProperty to = toMap.get(path);
        WzCanvasProperty from = fromMap.get(path);
        if (to == null || from == null) {
            return false;
        }
        if (refreshPreview) {
            curTo = to;
            curFrom = from;
        }

        BufferedImage image;
        if (options.autoRepairOnReplace()) {
            BufferedImage src = from.getPngImage(false);
            if (src == null) {
                if (refreshPreview) {
                    JOptionPane.showMessageDialog(this, "替换图解码失败，无法自动修补: " + path, "替换", JOptionPane.WARNING_MESSAGE);
                }
                return false;
            }
            int tw = to.getWidth();
            int th = to.getHeight();
            if (tw <= 0 || th <= 0) {
                if (refreshPreview) {
                    JOptionPane.showMessageDialog(this, "原图尺寸无效，无法自动修补: " + path, "替换", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
            int sw = src.getWidth();
            int sh = src.getHeight();
            if (sw <= 0 || sh <= 0) {
                if (refreshPreview) {
                    JOptionPane.showMessageDialog(this, "替换图尺寸无效，无法自动修补: " + path, "替换", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
            WzPngFormat leftFormat = to.getFormat();
            int leftScale = to.getScale();
            image = fitImageToCanvas(src, tw, th, CanvasFitMode.FIT_INSIDE);
            to.setPng(image, leftFormat, leftScale, Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        } else {
            image = from.getPngImage(false);
            if (image == null) {
                if (refreshPreview) {
                    JOptionPane.showMessageDialog(this, "替换图解码失败: " + path, "替换", JOptionPane.WARNING_MESSAGE);
                }
                return false;
            }
            to.setPng(image, from.getFormat(), from.getScale(),
                    Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        }

        to.clearImage();
        if (refreshPreview && (path.equals(stringList.getSelectedValue()) || stringList.getSelectedValuesList().contains(path))) {
            imagePanel1.setImage(image);
            refreshLeftImageInfo();
        }

        if (options.includeChildren()) {
            List<WzImageProperty> children = new ArrayList<>();
            from.getChildren().forEach(child -> children.add(child.deepClone(to)));
            to.replaceChildrenList(children);
        }

        applyOriginReplace(path, refreshPreview, options.replaceOrigin());

        if (markChanged) {
            changedPaths.add(path);
        }
        if (advanceAfter) {
            int index = listModel.indexOf(path);
            if (index >= 0 && index + 1 < listModel.getSize()) {
                stringList.setSelectedIndex(index + 1);
            }
        }
        if (updateStats) {
            updateStatusWithStats();
        }
        return true;
    }

    private void applyOriginReplace(String canvasPath) {
        applyOriginReplace(canvasPath, true, replaceOrigin.isSelected());
    }

    private void applyOriginReplace(String canvasPath, boolean refreshPreview, boolean replaceOriginSelected) {
        if (!replaceOriginSelected) {
            return;
        }
        CanvasOriginCache.OriginEntry fromEntry = fromOriginCache.get(canvasPath);
        CanvasOriginCache.OriginEntry toEntry = toOriginCache.get(canvasPath);
        if (!fromEntry.hasOrigin() || !toEntry.found()) {
            return;
        }
        if (CanvasOriginCache.applyOrigin(toEntry.metaNode(), fromEntry.x(), fromEntry.y())) {
            toOriginCache.put(canvasPath, CanvasOriginCache.OriginEntry.of(
                    toEntry.metaNode(), toEntry.metaPath(), fromEntry.x(), fromEntry.y(), true));
            if (refreshPreview && canvasPath.equals(stringList.getSelectedValue())) {
                refreshLeftImageInfo();
            }
        }
    }

    private void batchReplaceSameSize() {
        if (batchSameSizeReplaceRunning) {
            JOptionPane.showMessageDialog(this,
                    "同尺寸批量替换正在执行中，请等待当前任务完成。",
                    "同尺寸批量替换", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int totalInList = listModel.getSize();
        List<String> targets = collectSameSizeDiffPathsInCurrentList();
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    String.format("当前列表共 %d 张图片，没有可批量替换的同尺寸差异项。", totalInList),
                    "同尺寸批量替换", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("当前共 %d 张图片，存在 %d 张尺寸相同但内容不一样的图片，是否批量替换？",
                        totalInList, targets.size()),
                "同尺寸批量替换", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        startBatchSameSizeReplace(targets);
    }

    private void startBatchSameSizeReplace(List<String> targets) {
        batchSameSizeReplaceRunning = true;
        setBatchControlsEnabled(false);
        batchProgressBar.setVisible(true);
        batchProgressBar.setValue(0);
        batchProgressBar.setString("0 / " + targets.size() + " (0%)");
        setStatus("同尺寸批量替换准备执行...");

        ReplaceOptions options = new ReplaceOptions(
                autoRepairOnReplace.isSelected(), includeChildren.isSelected(), replaceOrigin.isSelected());

        SwingWorker<BatchReplaceResult, BatchReplaceProgress> worker = new SwingWorker<>() {
            @Override
            protected BatchReplaceResult doInBackground() {
                int success = 0;
                int failed = 0;
                int total = targets.size();
                long lastPublishAt = 0L;
                List<String> changed = new ArrayList<>();
                for (int i = 0; i < total; i++) {
                    String path = targets.get(i);
                    boolean ok = replaceSinglePath(path, false, false, false, options, false);
                    if (ok) {
                        success++;
                        changed.add(path);
                    } else {
                        failed++;
                    }
                    int done = i + 1;
                    long now = System.currentTimeMillis();
                    if (done == total || done == 1 || now - lastPublishAt >= 120L) {
                        publish(new BatchReplaceProgress(done, total, success, failed, path));
                        lastPublishAt = now;
                    }
                }
                return new BatchReplaceResult(total, success, failed, changed);
            }

            @Override
            protected void process(List<BatchReplaceProgress> chunks) {
                BatchReplaceProgress progress = chunks.get(chunks.size() - 1);
                int percent = progress.percent();
                batchProgressBar.setValue(percent);
                batchProgressBar.setString(progress.done() + " / " + progress.total() + " (" + percent + "%)");
                setStatus(String.format("同尺寸批量替换中：%d / %d，成功 %d，失败 %d，当前 %s",
                        progress.done(), progress.total(), progress.success(), progress.failed(), progress.path()));
            }

            @Override
            protected void done() {
                try {
                    BatchReplaceResult result = get();
                    batchProgressBar.setValue(100);
                    batchProgressBar.setString(result.total() + " / " + result.total() + " (100%)");
                    changedPaths.addAll(result.changedPaths());
                    refreshListKeepingFilter();
                    setStatus(String.format("同尺寸批量替换完成：共 %d，成功 %d，失败 %d。",
                            result.total(), result.success(), result.failed()));
                    String selectedPath = stringList.getSelectedValue();
                    if (selectedPath != null) {
                        onStringSelected(selectedPath);
                    }
                } catch (Exception ex) {
                    setStatus("同尺寸批量替换失败：" + ex.getMessage());
                    JOptionPane.showMessageDialog(ImageCompareDialog.this,
                            "同尺寸批量替换失败：\n" + ex.getMessage(),
                            "同尺寸批量替换", JOptionPane.ERROR_MESSAGE);
                } finally {
                    batchSameSizeReplaceRunning = false;
                    setBatchControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void setBatchControlsEnabled(boolean enabled) {
        includeChildren.setEnabled(enabled);
        replaceOrigin.setEnabled(enabled);
        autoRepairOnReplace.setEnabled(enabled);
        repairSizeBtn.setEnabled(enabled);
        sameSizeFilterBtn.setEnabled(enabled);
        sizeMismatchFilterBtn.setEnabled(enabled);
        originMismatchFilterBtn.setEnabled(enabled);
        imageDiffFilterBtn.setEnabled(enabled);
        batchSameSizeReplaceBtn.setEnabled(enabled);
        replaceBtn.setEnabled(enabled);
        stringList.setEnabled(enabled);
    }

    private record ReplaceOptions(boolean autoRepairOnReplace, boolean includeChildren, boolean replaceOrigin) {
    }

    private record BatchReplaceProgress(int done, int total, int success, int failed, String path) {
        int percent() {
            if (total <= 0) {
                return 0;
            }
            return Math.min(100, Math.max(0, (int) Math.round(done * 100.0 / total)));
        }
    }

    private record BatchReplaceResult(int total, int success, int failed, List<String> changedPaths) {
    }

    private List<String> collectSameSizeDiffPathsInCurrentList() {
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            String path = listModel.get(i);
            if (pathHasSameSize(path) && pathHasImageDifference(path)) {
                targets.add(path);
            }
        }
        return targets;
    }

    /** 批量操作后刷新列表显示（保留当前筛选条件，更新已替换项颜色）。 */
    private void refreshListKeepingFilter() {
        if (isAnyFilterActive()) {
            rebuildFilteredList();
        } else {
            stringList.repaint();
            updateStatusWithStats();
        }
    }

    private void restoreListSelection(List<String> preferredPaths) {
        if (preferredPaths.isEmpty()) {
            if (listModel.isEmpty()) {
                curTo = null;
                curFrom = null;
                imagePanel1.setImage(null);
                imagePanel2.setImage(null);
                updateDialogTitle(null);
            } else {
                stringList.setSelectedIndex(0);
                stringList.ensureIndexIsVisible(0);
                onStringSelected(listModel.get(0));
            }
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (String path : preferredPaths) {
            int idx = listModel.indexOf(path);
            if (idx >= 0) {
                indices.add(idx);
            }
        }
        if (indices.isEmpty()) {
            if (!listModel.isEmpty()) {
                stringList.setSelectedIndex(0);
                stringList.ensureIndexIsVisible(0);
                onStringSelected(listModel.get(0));
            }
            return;
        }
        int[] arr = indices.stream().mapToInt(Integer::intValue).toArray();
        stringList.setSelectedIndices(arr);
        stringList.ensureIndexIsVisible(arr[0]);
        onStringSelected(listModel.get(arr[0]));
    }

    /**
     * 将左侧「原图」按右侧「替换」图的画布尺寸调整：不拉伸变形。
     */
    private void repairImageSizeToReference() {
        if (batchSameSizeReplaceRunning) {
            return;
        }
        List<String> selected = stringList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        if (selected.size() > 1) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定对选中的 " + selected.size() + " 张图片进行尺寸修补？",
                    "批量尺寸修补", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            for (String path : selected) {
                repairSinglePath(path, false);
            }
            refreshListKeepingFilter();
            return;
        }
        repairSinglePath(selected.get(0), true);
    }

    private boolean repairSinglePath(String path, boolean advanceAfter) {
        WzCanvasProperty to = toMap.get(path);
        WzCanvasProperty from = fromMap.get(path);
        if (to == null || from == null) {
            return false;
        }
        curTo = to;
        curFrom = from;

        BufferedImage src = to.getPngImage(false);
        if (src == null) {
            JOptionPane.showMessageDialog(this, "原图解码失败，无法修补: " + path, "图片尺寸修补", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        int tw = from.getWidth();
        int th = from.getHeight();
        if (tw <= 0 || th <= 0) {
            JOptionPane.showMessageDialog(this, "替换图尺寸无效: " + path, "图片尺寸修补", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            JOptionPane.showMessageDialog(this, "原图尺寸无效: " + path, "图片尺寸修补", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        BufferedImage out = fitImageToCanvas(src, tw, th, CanvasFitMode.FIT_TARGET_WIDTH);

        WzPngFormat leftFormat = to.getFormat();
        int leftScale = to.getScale();
        to.setPng(out, leftFormat, leftScale, Deflater.BEST_COMPRESSION, WzPngZlibCompressMode.FILTERED);
        to.clearImage();

        if (path.equals(stringList.getSelectedValue()) || stringList.getSelectedValuesList().contains(path)) {
            imagePanel1.setImage(out);
            refreshLeftImageInfo();
        }

        changedPaths.add(path);
        if (advanceAfter) {
            int index = listModel.indexOf(path);
            if (index >= 0 && index + 1 < listModel.getSize()) {
                stringList.setSelectedIndex(index + 1);
            }
        }
        updateStatusWithStats();
        return true;
    }

    private void updateStatusWithStats() {
        int total = getTotalPathCount();
        int sameSizeDiff = 0;
        int sizeMismatch = 0;
        int originMismatch = 0;
        int imageDiff = 0;
        for (String path : getFullPathSourceOrder()) {
            if (pathHasImageDifference(path)) {
                imageDiff++;
            }
            if (pathHasSameSize(path) && pathHasImageDifference(path)) {
                sameSizeDiff++;
            }
            if (pathHasSizeMismatch(path)) {
                sizeMismatch++;
            }
            if (pathHasOriginMismatch(path)) {
                originMismatch++;
            }
        }
        setStatus(String.format("已修改 %d / %d 条 | 同尺寸差异 %d | 尺寸不同 %d | 描点不同 %d | 图片差异 %d",
                changedPaths.size(), total, sameSizeDiff, sizeMismatch, originMismatch, imageDiff));
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
        String path = left.getPath();
        widthLabel1.setText("Width: " + left.getWidth());
        heightLabel1.setText("Height: " + left.getHeight());
        formatLabel1.setText("Format: " + left.getFormat());
        scaleLabel1.setText("Scale: " + left.getScale());

        widthLabel2.setText("Width: " + right.getWidth());
        heightLabel2.setText("Height: " + right.getHeight());
        formatLabel2.setText("Format: " + right.getFormat());
        scaleLabel2.setText("Scale: " + right.getScale());

        CanvasOriginCache.OriginEntry leftOrigin = toOriginCache.get(path);
        CanvasOriginCache.OriginEntry rightOrigin = fromOriginCache.get(path);
        applyOriginLabels(originXLabel1, originYLabel1, leftOrigin, rightOrigin, true);
        applyOriginLabels(originXLabel2, originYLabel2, rightOrigin, leftOrigin, false);

        setOriginalInfoDiffColor(widthLabel1, left.getWidth() != right.getWidth());
        setOriginalInfoDiffColor(heightLabel1, left.getHeight() != right.getHeight());
        setOriginalInfoDiffColor(formatLabel1, left.getFormat() != right.getFormat());
        setOriginalInfoDiffColor(scaleLabel1, left.getScale() != right.getScale());
    }

    private static void applyOriginLabels(JLabel xLabel, JLabel yLabel,
                                          CanvasOriginCache.OriginEntry entry,
                                          CanvasOriginCache.OriginEntry other,
                                          boolean highlightDiff) {
        if (!entry.hasOrigin()) {
            xLabel.setText("Origin: 未找到描点");
            yLabel.setText("");
            String metaHint = entry.metaPath() != null ? entry.metaPath() : "无";
            xLabel.setToolTipText(entry.found()
                    ? "已定位属性节点 " + metaHint + "，但无 origin 子节点"
                    : "未在视图中找到对应属性 WZ 节点（请确认已加载 Map_000.wz / UI_000.wz 等）");
            yLabel.setToolTipText(null);
            if (highlightDiff) {
                boolean differs = entry.hasOrigin() != other.hasOrigin()
                        || (entry.hasOrigin() && other.hasOrigin()
                        && (entry.x() != other.x() || entry.y() != other.y()));
                setOriginalInfoDiffColor(xLabel, differs);
            }
            return;
        }
        xLabel.setText("Origin X: " + entry.x());
        yLabel.setText("Origin Y: " + entry.y());
        xLabel.setToolTipText(null);
        yLabel.setToolTipText(null);
        if (highlightDiff) {
            if (!other.hasOrigin()) {
                setOriginalInfoDiffColor(xLabel, true);
                setOriginalInfoDiffColor(yLabel, true);
            } else {
                setOriginalInfoDiffColor(xLabel, entry.x() != other.x());
                setOriginalInfoDiffColor(yLabel, entry.y() != other.y());
            }
        }
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

    private void showListContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = stringList.locationToIndex(e.getPoint());
        if (index < 0 || index >= listModel.size()) {
            return;
        }
        stringList.setSelectedIndex(index);
        String path = listModel.get(index);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem viewImageItem = new JMenuItem("查看图片");
        viewImageItem.addActionListener(ev -> navigateToPath(toEditPane, path));
        JMenuItem viewMetaItem = new JMenuItem("查看属性");
        viewMetaItem.addActionListener(ev -> {
            String metaPath = toOriginCache.resolveMetadataPath(path);
            if (metaPath == null) {
                JOptionPane.showMessageDialog(this,
                        "未找到对应属性 WZ 节点：\n" + path,
                        "查看属性", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            navigateToPath(toEditPane, metaPath);
        });
        menu.add(viewImageItem);
        menu.add(viewMetaItem);
        menu.show(stringList, e.getX(), e.getY());
    }

    private static void navigateToPath(EditPane editPane, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        editPane.focusNodeByPath(Arrays.asList(path.replace('\\', '/').split("/")));
    }

    private void updateDialogTitle(String selectedPath) {
        if (selectedPath == null || selectedPath.isEmpty()) {
            setTitle(BASE_TITLE);
        } else {
            setTitle(BASE_TITLE + "  " + selectedPath.replace('/', '\\'));
        }
    }

    private void onStringSelected(String value) {
        curTo = toMap.get(value);
        curFrom = fromMap.get(value);
        updateDialogTitle(value);
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

    private boolean pathHasSameSize(String path) {
        WzCanvasProperty left = toMap.get(path);
        WzCanvasProperty right = fromMap.get(path);
        return left != null && right != null
                && left.getWidth() == right.getWidth()
                && left.getHeight() == right.getHeight();
    }

    private boolean pathHasOriginMismatch(String path) {
        if (!originCacheReady) {
            return false;
        }
        CanvasOriginCache.OriginEntry left = toOriginCache.get(path);
        CanvasOriginCache.OriginEntry right = fromOriginCache.get(path);
        if (!left.hasOrigin() || !right.hasOrigin()) {
            return left.hasOrigin() != right.hasOrigin();
        }
        return left.x() != right.x() || left.y() != right.y();
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
        if (sameSizeFilterActive && !(pathHasSameSize(path) && pathHasImageDifference(path))) {
            return false;
        }
        if (sizeMismatchFilterActive && !pathHasSizeMismatch(path)) {
            return false;
        }
        if (originMismatchFilterActive && !pathHasOriginMismatch(path)) {
            return false;
        }
        if (imageDiffFilterActive && !pathHasImageDifference(path)) {
            return false;
        }
        return true;
    }

    private boolean isAnyFilterActive() {
        return sameSizeFilterActive || sizeMismatchFilterActive
                || originMismatchFilterActive || imageDiffFilterActive;
    }

    private List<String> getFullPathSourceOrder() {
        if (!fullPathsInOrder.isEmpty()) {
            return fullPathsInOrder;
        }
        return Collections.list(listModel.elements());
    }

    private void deactivateAllFilters() {
        sameSizeFilterActive = false;
        sizeMismatchFilterActive = false;
        originMismatchFilterActive = false;
        imageDiffFilterActive = false;
        updateAllFilterButtonStates();
    }

    private void updateAllFilterButtonStates() {
        updateFilterButtonState(sameSizeFilterBtn, sameSizeFilterActive, "同尺寸筛选");
        updateFilterButtonState(sizeMismatchFilterBtn, sizeMismatchFilterActive, "不同尺寸筛选");
        updateFilterButtonState(originMismatchFilterBtn, originMismatchFilterActive, "描点差异筛选");
        updateFilterButtonState(imageDiffFilterBtn, imageDiffFilterActive, "图片差异筛选");
    }

    private void updateFilterButtonState(JButton button, boolean active, String baseLabel) {
        button.setText(active ? "✓ " + baseLabel : baseLabel);
        button.setOpaque(true);
        button.setBackground(active ? new Color(200, 230, 255) : UIManager.getColor("Button.background"));
    }

    private String buildFilterStatusPrefix() {
        List<String> parts = new ArrayList<>();
        if (sameSizeFilterActive) {
            parts.add("同尺寸差异");
        }
        if (sizeMismatchFilterActive) {
            parts.add("不同尺寸");
        }
        if (originMismatchFilterActive) {
            parts.add("描点差异");
        }
        if (imageDiffFilterActive) {
            parts.add("图片差异");
        }
        return String.join(" + ", parts);
    }

    private String buildFilterStatusText(int matched, int total) {
        return String.format("筛选[%s] %d / %d 条（已修改 %d 条）",
                buildFilterStatusPrefix(), matched, total, changedPaths.size());
    }

    /**
     * 按当前已启用的筛选条件（AND 叠加）重建左侧列表。
     *
     * @return 无筛选或筛选结果非空时返回 true
     */
    private boolean rebuildFilteredList() {
        List<String> sourceOrder = getFullPathSourceOrder();
        if (!isAnyFilterActive()) {
            listModel.clear();
            listModel.addAll(sourceOrder);
            restoreListSelection(Collections.emptyList());
            updateStatusWithStats();
            return true;
        }
        List<String> filtered = new ArrayList<>();
        for (String path : sourceOrder) {
            if (pathPassesActiveFilter(path)) {
                filtered.add(path);
            }
        }
        List<String> selectedPaths = new ArrayList<>(stringList.getSelectedValuesList());
        listModel.clear();
        listModel.addAll(filtered);
        restoreListSelection(selectedPaths);
        setStatus(buildFilterStatusText(filtered.size(), sourceOrder.size()));
        return !filtered.isEmpty();
    }

    private void toggleFilter(java.util.function.BooleanSupplier isActive,
                              java.util.function.Consumer<Boolean> setActive,
                              JButton button, String label) {
        if (!isActive.getAsBoolean() && getFullPathSourceOrder().isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可筛选的路径。", label, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean enabling = !isActive.getAsBoolean();
        setActive.accept(enabling);
        updateFilterButtonState(button, enabling, label);
        if (!rebuildFilteredList()) {
            if (enabling) {
                setActive.accept(false);
                updateFilterButtonState(button, false, label);
                JOptionPane.showMessageDialog(this,
                        "当前筛选组合下无匹配项。",
                        label, JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /** 将 collections 刷入左侧列表；若正在筛选，只追加符合当前筛选条件的项 */
    private void flushCollectionsToListModel() {
        if (collections.isEmpty()) {
            return;
        }
        if (isAnyFilterActive()) {
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

    private void toggleSameSizeFilter() {
        toggleFilter(() -> sameSizeFilterActive, active -> sameSizeFilterActive = active,
                sameSizeFilterBtn, "同尺寸筛选");
    }

    private void toggleSizeMismatchFilter() {
        toggleFilter(() -> sizeMismatchFilterActive, active -> sizeMismatchFilterActive = active,
                sizeMismatchFilterBtn, "不同尺寸筛选");
    }

    private void toggleOriginMismatchFilter() {
        toggleFilter(() -> originMismatchFilterActive, active -> originMismatchFilterActive = active,
                originMismatchFilterBtn, "描点差异筛选");
    }

    private void toggleImageDiffFilter() {
        toggleFilter(() -> imageDiffFilterActive, active -> imageDiffFilterActive = active,
                imageDiffFilterBtn, "图片差异筛选");
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
        setStatus("扫描中 " + getTotalPathCount() + " 条...");

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
        setStatus("正在建立描点索引...");
        toOriginCache.buildIndex();
        fromOriginCache.buildIndex();
        Collection<String> paths = fullPathsInOrder.isEmpty() ? toMap.keySet() : fullPathsInOrder;
        toOriginCache.preload(paths);
        fromOriginCache.preload(paths);
        originCacheReady = true;
        if (listModel.getSize() == 0) {
            if (isAnyFilterActive() && !toMap.isEmpty()) {
                setStatus("扫描完毕：筛选[" + buildFilterStatusPrefix() + "] 下无匹配项，可取消筛选条件查看全部。");
            } else {
                setStatus("扫描完毕，找不到数据。");
            }
        } else {
            if (stringList.getSelectedIndex() < 0) {
                stringList.setSelectedIndex(0);
                stringList.ensureIndexIsVisible(0);
                onStringSelected(listModel.get(0));
            }
            updateStatusWithStats();
        }
    }

    private void requestDialogClose() {
        if (batchSameSizeReplaceRunning) {
            JOptionPane.showMessageDialog(this,
                    "同尺寸批量替换正在执行中，请等待完成后再关闭窗口。",
                    "图片对比", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dispose();
    }

    /**
     * 在对话框关闭后清理数据
     */
    private void onDialogClosed() {
        if (dialogClosed) {
            return;
        }
        dialogClosed = true;
        saveLayoutPreferences();
        if (saveLayoutTimer != null) {
            saveLayoutTimer.stop();
        }
        listModel.clear();
        toMap.clear();
        fromMap.clear();
        fullPathsInOrder.clear();
        changedPaths.clear();
        toOriginCache.clear();
        fromOriginCache.clear();
        originCacheReady = false;
        deactivateAllFilters();
        curTo = null;
        curFrom = null;
    }

}
