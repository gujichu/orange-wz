package orange.wz.gui.component.imageeditor;

import orange.wz.gui.Icons;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.FileDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImageEditorFrame extends JFrame {

    private static ImageEditorFrame instance;

    private final ImageEditorPrefs prefs = new ImageEditorPrefs();
    private final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final Map<ImageEditorCanvas, File> fileMap = new HashMap<>();
    private final Map<ImageEditorCanvas, JScrollPane> scrollMap = new HashMap<>();

    private final JLabel statusLabel = new JLabel("就绪");
    private final JLabel imageInfoLabel = new JLabel("无图片");
    private final JSpinner brushSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
    private final JSpinner toleranceSpinner = new JSpinner(new SpinnerNumberModel(32, 0, 128, 1));
    private final JSpinner wandToleranceSpinner = new JSpinner(new SpinnerNumberModel(32, 0, 255, 1));
    private final JSpinner mosaicSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 64, 1));
    private final JSlider rgbR = createSlider(0);
    private final JSlider rgbG = createSlider(0);
    private final JSlider rgbB = createSlider(0);
    private final JSlider hsvH = createSlider(0, -180, 180);
    private final JSlider hsvS = createSlider(0, -100, 100);
    private final JSlider hsvV = createSlider(0, -100, 100);
    private final JSlider alphaSlider = createSlider(255);
    private final JSlider qualitySlider = createSlider(90, 1, 100);
    private final JRadioButton fmtPng = new JRadioButton("PNG", true);
    private final JRadioButton fmtJpg = new JRadioButton("JPG");
    private final JRadioButton fmtGif = new JRadioButton("GIF");
    private final JRadioButton fmtBmp = new JRadioButton("BMP");
    private final JSpinner zoomSpinner = new JSpinner(new SpinnerNumberModel(100, 5, 3200, 5));
    private final JSpinner canvasWSpinner = new JSpinner(new SpinnerNumberModel(800, 1, 16384, 1));
    private final JSpinner canvasHSpinner = new JSpinner(new SpinnerNumberModel(600, 1, 16384, 1));
    private final JSpinner imageWSpinner = new JSpinner(new SpinnerNumberModel(800, 1, 16384, 1));
    private final JSpinner imageHSpinner = new JSpinner(new SpinnerNumberModel(600, 1, 16384, 1));

    private static final int PLUS_TAB_PLACEHOLDER = -1;
    private int plusTabIndex = PLUS_TAB_PLACEHOLDER;

    private final ImageEditorColorPalette colorPalette;
    private final ImageEditorHistoryWindow historyWindow;
    private final ImageEditorLayerPanel layerPanel = new ImageEditorLayerPanel();
    private final JToggleButton historyToggleBtn = new JToggleButton("历史记录");
    private final JPanel currentColorSwatch = new JPanel();
    private final Map<ImageEditorTool, AbstractButton> toolButtons = new HashMap<>();

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            if (instance == null || !instance.isDisplayable()) {
                instance = new ImageEditorFrame();
            }
            instance.setVisible(true);
            instance.toFront();
            instance.requestFocus();
        });
    }

    private ImageEditorFrame() {
        super("图片编辑器");
        colorPalette = new ImageEditorColorPalette(prefs);
        historyWindow = new ImageEditorHistoryWindow(this);
        historyWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                historyToggleBtn.setSelected(false);
            }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(MainFrame.getInstance());
        if (MainFrame.getInstance() != null) {
            setIconImage(MainFrame.getInstance().getIconImage());
        }
        setLayout(new BorderLayout(2, 2));

        add(buildLeftToolbar(), BorderLayout.WEST);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildRightPanel(), BorderLayout.EAST);
        add(buildBottomBar(), BorderLayout.SOUTH);

        applyPrefsToControls();
        wireGlobalListeners();
        addNewTab(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                collectPrefsFromControls();
                prefs.save();
                for (ImageEditorCanvas canvas : scrollMap.keySet()) {
                    canvas.disposeResources();
                }
                scrollMap.clear();
                fileMap.clear();
                historyWindow.setVisible(false);
                historyWindow.disposeWindow();
            }
        });

        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                collectPrefsFromControls();
                prefs.save();
                dispose();
            }
        });
    }

    private ImageEditorCanvas activeCanvas() {
        Component c = tabbedPane.getSelectedComponent();
        if (c instanceof JScrollPane scroll) {
            Component view = scroll.getViewport().getView();
            if (view instanceof ImageEditorCanvas canvas) {
                return canvas;
            }
        }
        return null;
    }

    private void addNewTab(File file) {
        ImageEditorCanvas canvas = new ImageEditorCanvas();
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getHorizontalScrollBar().setUnitIncrement(48);
        scroll.setBorder(new EmptyBorder(4, 4, 4, 4));

        fileMap.put(canvas, file);
        scrollMap.put(canvas, scroll);

        String title = file != null ? file.getName() : "未命名";
        removePlusTabIfPresent();
        tabbedPane.addTab(title, scroll);
        ensurePlusTab();
        tabbedPane.setSelectedComponent(scroll);

        wireCanvas(canvas);
        applyPrefsToCanvas(canvas);
        if (file != null) {
            canvas.loadFile(file, f -> {
                tabbedPane.setTitleAt(tabbedPane.indexOfComponent(scroll), f.getName());
                fileMap.put(canvas, f);
                syncSizeSpinners(canvas);
            });
        } else {
            syncSizeSpinners(canvas);
        }
        refreshHistoryWindow(canvas);
        historyWindow.bindCanvas(canvas, () -> refreshHistoryWindow(canvas));
        layerPanel.bind(canvas, () -> layerPanel.refresh());
    }

    private void ensurePlusTab() {
        if (plusTabIndex >= 0 && plusTabIndex < tabbedPane.getTabCount()) {
            return;
        }
        tabbedPane.addTab("+", (Component) null);
        plusTabIndex = tabbedPane.getTabCount() - 1;
        JButton plusBtn = new JButton("+ 新标签");
        plusBtn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        plusBtn.addActionListener(e -> {
            tabbedPane.setSelectedIndex(Math.max(0, plusTabIndex - 1));
            addNewTab(null);
        });
        tabbedPane.setTabComponentAt(plusTabIndex, plusBtn);
    }

    private void removePlusTabIfPresent() {
        if (plusTabIndex >= 0 && plusTabIndex < tabbedPane.getTabCount()) {
            tabbedPane.removeTabAt(plusTabIndex);
        }
        plusTabIndex = -1;
    }

    private void wireCanvas(ImageEditorCanvas canvas) {
        canvas.setColorPickedListener(c -> {
            prefs.foregroundColor = c;
            currentColorSwatch.setBackground(c);
            colorPalette.setSelectedColor(c);
        });
        canvas.setStatusListener(text -> statusLabel.setText(text));
        canvas.setImageChangedListener(() -> {
            updateImageInfo(canvas);
            syncSizeSpinners(canvas);
        });
        canvas.setHistoryChangedListener(() -> refreshHistoryWindow(canvas));
        canvas.setHistoryIndexChangedListener(() -> {
            if (historyWindow.isShowingWindow()) {
                historyWindow.refreshSelection();
            }
        });
        canvas.setZoomChangedListener(z -> {
            if (activeCanvas() == canvas) {
                zoomSpinner.setValue((int) Math.round(z * 100));
            }
        });
        canvas.setFilesDroppedListener(this::openDroppedFiles);
        canvas.setLayersChangedListener(() -> {
            if (activeCanvas() == canvas) {
                layerPanel.refresh();
            }
        });
    }

    private void openDroppedFiles(List<File> files) {
        for (int i = 0; i < files.size(); i++) {
            if (i == 0) {
                ImageEditorCanvas canvas = activeCanvas();
                if (canvas != null && canvas.getImage() == null) {
                    File f = files.get(i);
                    fileMap.put(canvas, f);
                    canvas.loadFile(f, loaded -> {
                        int idx = tabbedPane.indexOfComponent(scrollMap.get(canvas));
                        if (idx >= 0) {
                            tabbedPane.setTitleAt(idx, loaded.getName());
                        }
                        fileMap.put(canvas, loaded);
                        updateImageInfo(canvas);
                    });
                    continue;
                }
            }
            addNewTab(files.get(i));
        }
    }

    private void wireGlobalListeners() {
        tabbedPane.addChangeListener(e -> {
            if (plusTabIndex >= 0 && tabbedPane.getSelectedIndex() == plusTabIndex) {
                tabbedPane.setSelectedIndex(Math.max(0, plusTabIndex - 1));
                return;
            }
            ImageEditorCanvas canvas = activeCanvas();
            if (canvas != null) {
                historyWindow.bindCanvas(canvas, () -> {
                if (historyWindow.isShowingWindow()) {
                    historyWindow.refreshSelection();
                }
            });
                layerPanel.bind(canvas, () -> layerPanel.refresh());
                updateImageInfo(canvas);
                syncSizeSpinners(canvas);
                zoomSpinner.setValue((int) Math.round(canvas.getZoom() * 100));
                selectTool(canvas.getCurrentTool());
                layerPanel.refresh();
            }
        });

        colorPalette.setColorChangedCallback(() -> {
            ImageEditorCanvas canvas = activeCanvas();
            if (canvas != null) {
                canvas.setForegroundColor(prefs.foregroundColor);
                currentColorSwatch.setBackground(prefs.foregroundColor);
            }
        });

        brushSpinner.addChangeListener(e -> {
            prefs.brushSize = (Integer) brushSpinner.getValue();
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.setBrushSize(prefs.brushSize);
            }
        });
        toleranceSpinner.addChangeListener(e -> {
            prefs.fillTolerance = (Integer) toleranceSpinner.getValue();
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.setFillTolerance(prefs.fillTolerance);
            }
        });
        wandToleranceSpinner.addChangeListener(e -> {
            prefs.magicWandTolerance = (Integer) wandToleranceSpinner.getValue();
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.setMagicWandTolerance(prefs.magicWandTolerance);
            }
        });
        mosaicSpinner.addChangeListener(e -> prefs.mosaicBlockSize = (Integer) mosaicSpinner.getValue());
        qualitySlider.addChangeListener(e -> prefs.quality = qualitySlider.getValue());

        ButtonGroup fmtGroup = new ButtonGroup();
        fmtGroup.add(fmtPng);
        fmtGroup.add(fmtJpg);
        fmtGroup.add(fmtGif);
        fmtGroup.add(fmtBmp);
        fmtPng.addActionListener(e -> prefs.saveFormat = "png");
        fmtJpg.addActionListener(e -> prefs.saveFormat = "jpg");
        fmtGif.addActionListener(e -> prefs.saveFormat = "gif");
        fmtBmp.addActionListener(e -> prefs.saveFormat = "bmp");

        zoomSpinner.addChangeListener(e -> {
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.setZoom(((Integer) zoomSpinner.getValue()) / 100.0);
            }
        });
    }

    private JComponent buildLeftToolbar() {
        JToolBar bar = new JToolBar(SwingConstants.VERTICAL);
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(4, 4, 4, 4));

        addToolBtn(bar, "新建", Icons.AiOutlinePlus, e -> createNewImage());
        addToolBtn(bar, "打开", Icons.FcFileIcon, e -> openImages());
        addToolBtn(bar, "保存", Icons.AiOutlineSaveIcon, e -> saveImage());
        bar.addSeparator();

        for (ImageEditorTool tool : ImageEditorTool.values()) {
            JToggleButton btn = new JToggleButton(tool.getLabel());
            btn.setPreferredSize(new Dimension(72, 28));
            btn.setMaximumSize(new Dimension(72, 28));
            btn.addActionListener(e -> selectTool(tool));
            toolButtons.put(tool, btn);
            bar.add(btn);
        }

        bar.addSeparator();
        addToolBtn(bar, "上一步", null, e -> stepBack());
        addToolBtn(bar, "下一步", null, e -> stepForward());
        bar.addSeparator();
        addToolBtn(bar, "旋转90°", null, e -> rotate(90));
        addToolBtn(bar, "旋转-90°", null, e -> rotate(-90));
        addToolBtn(bar, "水平翻转", null, e -> flip(true));
        addToolBtn(bar, "垂直翻转", null, e -> flip(false));
        addToolBtn(bar, "复制", Icons.AiOutlineCopy, e -> {
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.copySelection();
            }
        });
        addToolBtn(bar, "粘贴", Icons.MdOutlineContentPaste, e -> {
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.pasteFromClipboard();
            }
        });

        return bar;
    }

    private void addToolBtn(JToolBar bar, String text, Icon icon, java.awt.event.ActionListener action) {
        JButton btn = icon != null ? new JButton(text, icon) : new JButton(text);
        btn.setPreferredSize(new Dimension(72, 28));
        btn.setMaximumSize(new Dimension(72, 28));
        btn.addActionListener(action);
        bar.add(btn);
    }

    private JComponent buildCenter() {
        tabbedPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        ensurePlusTab();
        return tabbedPane;
    }

    private JComponent buildRightPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 0, 0, 4));

        content.add(buildColorSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildToolParamsSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildHistoryToggleSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildTransformSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildAdjustSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildMosaicSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildSaveSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(64);
        scroll.setBorder(null);

        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(248, 0));
        right.add(scroll, BorderLayout.CENTER);
        right.add(layerPanel, BorderLayout.SOUTH);
        return right;
    }

    private JPanel buildColorSection() {
        JPanel p = section("颜色");
        currentColorSwatch.setPreferredSize(new Dimension(24, 24));
        currentColorSwatch.setBackground(prefs.foregroundColor);
        currentColorSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        currentColorSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        currentColorSwatch.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Color c = JColorChooser.showDialog(ImageEditorFrame.this, "当前颜色", prefs.foregroundColor);
                if (c != null) {
                    setForegroundColor(c);
                }
            }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        top.add(new JLabel("当前"));
        top.add(currentColorSwatch);
        JButton pickBtn = compactBtn("自定义");
        pickBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "自定义颜色", prefs.foregroundColor);
            if (c != null) {
                setForegroundColor(c);
            }
        });
        top.add(pickBtn);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(240, 28));
        p.add(top);
        colorPalette.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(colorPalette);
        return p;
    }

    private JPanel buildHistoryToggleSection() {
        JPanel p = section("历史");
        historyToggleBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyToggleBtn.setMaximumSize(new Dimension(240, 28));
        historyToggleBtn.addActionListener(e -> toggleHistoryWindow());
        p.add(wrap(historyToggleBtn));
        return p;
    }

    private void toggleHistoryWindow() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas != null) {
            historyWindow.bindCanvas(canvas, () -> {
                if (historyWindow.isShowingWindow()) {
                    historyWindow.refreshSelection();
                }
            });
        }
        historyWindow.toggle(this);
        historyToggleBtn.setSelected(historyWindow.isShowingWindow());
    }

    private JPanel buildToolParamsSection() {
        JPanel p = section("工具参数");
        p.add(row("画笔", brushSpinner));
        p.add(row("填充容差", toleranceSpinner));
        p.add(row("魔棒容差", wandToleranceSpinner));
        JLabel hint = new JLabel("<html><small>魔棒容差 0~255，越大选取范围越广</small></html>");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hint);
        return p;
    }

    private JPanel buildTransformSection() {
        JPanel p = section("变换");
        JSpinner angleSpinner = new JSpinner(new SpinnerNumberModel(0, -360, 360, 1));
        JButton rotateBtn = compactBtn("旋转");
        JButton clearBtn = compactBtn("清除选区");
        rotateBtn.addActionListener(e -> rotate((Integer) angleSpinner.getValue()));
        clearBtn.addActionListener(e -> {
            ImageEditorCanvas c = activeCanvas();
            if (c != null) {
                c.clearSelection();
            }
        });
        p.add(row("角度°", angleSpinner));
        p.add(btnRow(rotateBtn, clearBtn));
        return p;
    }

    private JPanel buildAdjustSection() {
        JPanel p = section("选区调整");
        p.add(compactSlider("R", rgbR));
        p.add(compactSlider("G", rgbG));
        p.add(compactSlider("B", rgbB));
        JButton rgbOffsetBtn = compactBtn("RGB偏移");
        JButton setRgbBtn = compactBtn("设RGB");
        rgbOffsetBtn.addActionListener(e -> applyRgbOffset());
        setRgbBtn.addActionListener(e -> applyCustomRgb());
        p.add(btnRow(rgbOffsetBtn, setRgbBtn));

        p.add(compactSlider("H°", hsvH));
        p.add(compactSlider("S%", hsvS));
        p.add(compactSlider("V%", hsvV));
        JButton hsvBtn = compactBtn("HSV偏移");
        hsvBtn.addActionListener(e -> applyHsvOffset());
        p.add(wrap(hsvBtn));

        p.add(compactSlider("透明", alphaSlider));
        JButton alphaBtn = compactBtn("应用透明");
        alphaBtn.addActionListener(e -> applyAlpha());
        p.add(wrap(alphaBtn));
        return p;
    }

    private JPanel buildMosaicSection() {
        JPanel p = section("马赛克");
        p.add(row("块大小", mosaicSpinner));
        JButton btn = compactBtn("应用马赛克");
        btn.addActionListener(e -> applyMosaic());
        p.add(wrap(btn));
        return p;
    }

    private JPanel buildSaveSection() {
        JPanel p = section("保存");
        JPanel fmtRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fmtRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fmtRow.setMaximumSize(new Dimension(240, 26));
        fmtPng.setMargin(new Insets(0, 2, 0, 2));
        fmtJpg.setMargin(new Insets(0, 2, 0, 2));
        fmtGif.setMargin(new Insets(0, 2, 0, 2));
        fmtBmp.setMargin(new Insets(0, 2, 0, 2));
        fmtRow.add(fmtPng);
        fmtRow.add(fmtJpg);
        fmtRow.add(fmtGif);
        fmtRow.add(fmtBmp);
        p.add(fmtRow);
        p.add(saveQualityRow());
        return p;
    }

    private JPanel saveQualityRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(240, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel("质量%");
        lbl.setPreferredSize(new Dimension(56, 20));
        JLabel val = new JLabel(String.valueOf(qualitySlider.getValue()));
        val.setPreferredSize(new Dimension(28, 20));
        qualitySlider.addChangeListener(e -> val.setText(String.valueOf(qualitySlider.getValue())));
        JPanel right = new JPanel(new BorderLayout());
        right.add(qualitySlider, BorderLayout.CENTER);
        right.add(val, BorderLayout.EAST);
        row.add(lbl, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private void createNewImage() {
        ImageEditorNewImageDialog dialog = new ImageEditorNewImageDialog(this);
        if (!dialog.showAndConfirm()) {
            return;
        }
        int canvasW = dialog.getCanvasWidth();
        int canvasH = dialog.getCanvasHeight();
        int imageW = dialog.getImageWidth();
        int imageH = dialog.getImageHeight();

        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() != null) {
            addNewTab(null);
            canvas = activeCanvas();
        }
        if (canvas == null) {
            return;
        }

        BufferedImage blank = ImageEditorUtil.createBlankImage(imageW, imageH);
        canvas.setImage(blank, "新建");
        if (canvasW != imageW || canvasH != imageH) {
            canvas.getLayerStack().resizeCanvas(canvasW, canvasH);
        }
        syncSizeSpinners(canvas);
        layerPanel.refresh();

        JScrollPane scroll = scrollMap.get(canvas);
        if (scroll != null) {
            int idx = tabbedPane.indexOfComponent(scroll);
            if (idx >= 0) {
                tabbedPane.setTitleAt(idx, "未命名");
            }
        }
        fileMap.put(canvas, null);
        statusLabel.setText(String.format("已新建 %d×%d 画布，图片 %d×%d", canvasW, canvasH, imageW, imageH));
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEtchedBorder());

        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        bar.add(statusLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.add(new JLabel("缩放%"));
        zoomSpinner.setPreferredSize(new Dimension(70, 22));
        right.add(zoomSpinner);

        right.add(new JLabel("画布"));
        canvasWSpinner.setPreferredSize(new Dimension(60, 22));
        canvasHSpinner.setPreferredSize(new Dimension(60, 22));
        right.add(canvasWSpinner);
        right.add(new JLabel("×"));
        right.add(canvasHSpinner);
        JButton applyCanvas = compactBtn("应用");
        applyCanvas.addActionListener(e -> applyCanvasSize());
        right.add(applyCanvas);

        right.add(new JLabel("图片"));
        imageWSpinner.setPreferredSize(new Dimension(60, 22));
        imageHSpinner.setPreferredSize(new Dimension(60, 22));
        right.add(imageWSpinner);
        right.add(new JLabel("×"));
        right.add(imageHSpinner);
        JButton applyImage = compactBtn("缩放");
        applyImage.addActionListener(e -> applyImageSize());
        right.add(applyImage);

        right.add(imageInfoLabel);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void selectTool(ImageEditorTool tool) {
        ImageEditorCanvas c = activeCanvas();
        if (c != null) {
            c.setCurrentTool(tool);
        }
        for (Map.Entry<ImageEditorTool, AbstractButton> e : toolButtons.entrySet()) {
            e.getValue().setSelected(e.getKey() == tool);
        }
    }

    private void setForegroundColor(Color c) {
        prefs.foregroundColor = c;
        currentColorSwatch.setBackground(c);
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas != null) {
            canvas.setForegroundColor(c);
        }
    }

    private void openImages() {
        List<File> files = FileDialog.chooseOpenFiles(new String[]{"png", "jpg", "jpeg", "gif", "bmp", "webp"});
        if (files.isEmpty()) {
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            if (i == 0 && activeCanvas() != null && activeCanvas().getImage() == null) {
                File f = files.get(i);
                ImageEditorCanvas canvas = activeCanvas();
                fileMap.put(canvas, f);
                canvas.loadFile(f, loaded -> {
                    int idx = tabbedPane.indexOfComponent(scrollMap.get(canvas));
                    if (idx >= 0) {
                        tabbedPane.setTitleAt(idx, loaded.getName());
                    }
                    fileMap.put(canvas, loaded);
                    updateImageInfo(canvas);
                });
            } else {
                addNewTab(files.get(i));
            }
        }
    }

    private void saveImage() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            JOptionPane.showMessageDialog(this, "没有可保存的图片", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        collectPrefsFromControls();
        String fmt = prefs.saveFormat;
        String[] filters = switch (fmt) {
            case "jpg" -> new String[]{"jpg", "jpeg"};
            case "gif" -> new String[]{"gif"};
            case "bmp" -> new String[]{"bmp"};
            default -> new String[]{"png"};
        };
        File current = fileMap.get(canvas);
        String baseName = current != null ? stripExtension(current.getName()) : "image";
        File target = FileDialog.chooseSaveFile(this, "保存图片", new File(baseName + "." + filters[0]), filters);
        if (target == null) {
            return;
        }
        try {
            ImageEditorUtil.saveImage(target, canvas.getImage(), fmt, prefs.quality / 100f);
            fileMap.put(canvas, target);
            int idx = tabbedPane.indexOfComponent(scrollMap.get(canvas));
            if (idx >= 0) {
                tabbedPane.setTitleAt(idx, target.getName());
            }
            updateImageInfo(canvas);
            statusLabel.setText("已保存: " + target.getName());
            JOptionPane.showMessageDialog(this, "保存成功", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stepBack() {
        ImageEditorCanvas c = activeCanvas();
        if (c != null) {
            c.stepBack();
        }
    }

    private void stepForward() {
        ImageEditorCanvas c = activeCanvas();
        if (c != null) {
            c.stepForward();
        }
    }

    private void rotate(int degrees) {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        canvas.applyAndRepaint("旋转 " + degrees + "°", () -> {
            if (canvas.hasExplicitSelection()) {
                ImageEditorSelection newSel = ImageEditorUtil.rotateRegion(
                        canvas.getEditImage(), canvas.getSelection(), degrees);
                canvas.setSelection(newSel);
            } else {
                canvas.replaceImage(ImageEditorUtil.rotateImage(canvas.getImage(), degrees));
            }
        });
        layerPanel.refresh();
        syncSizeSpinners(canvas);
    }

    private void flip(boolean horizontal) {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        ImageEditorSelection sel = canvas.hasExplicitSelection() ? canvas.getSelection() : null;
        canvas.applyAndRepaint(horizontal ? "水平翻转" : "垂直翻转", () -> {
            if (canvas.hasExplicitSelection()) {
                ImageEditorSelection newSel = horizontal
                        ? ImageEditorUtil.flipHorizontal(canvas.getEditImage(), sel)
                        : ImageEditorUtil.flipVertical(canvas.getEditImage(), sel);
                canvas.setSelection(newSel);
            } else {
                BufferedImage flat = ImageEditorUtil.deepCopy(canvas.getImage());
                if (horizontal) {
                    ImageEditorUtil.flipHorizontal(flat, null);
                } else {
                    ImageEditorUtil.flipVertical(flat, null);
                }
                canvas.replaceImage(flat);
            }
        });
        layerPanel.refresh();
    }

    private void applyRgbOffset() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        canvas.applyAndRepaint("RGB偏移", () ->
                ImageEditorUtil.adjustRgb(canvas.getEditImage(), canvas.getEffectiveSelection(),
                        rgbR.getValue(), rgbG.getValue(), rgbB.getValue()));
        resetSliders(rgbR, rgbG, rgbB);
    }

    private void applyCustomRgb() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        canvas.applyAndRepaint("设置RGB", () ->
                ImageEditorUtil.setRgb(canvas.getEditImage(), canvas.getEffectiveSelection(),
                        rgbR.getValue(), rgbG.getValue(), rgbB.getValue()));
    }

    private void applyHsvOffset() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        canvas.applyAndRepaint("HSV偏移", () ->
                ImageEditorUtil.adjustHsv(canvas.getEditImage(), canvas.getEffectiveSelection(),
                        hsvH.getValue(), hsvS.getValue(), hsvV.getValue()));
        resetSliders(hsvH, hsvS, hsvV);
    }

    private void applyAlpha() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        canvas.applyAndRepaint("透明度", () ->
                ImageEditorUtil.adjustAlpha(canvas.getEditImage(), canvas.getEffectiveSelection(), alphaSlider.getValue()));
    }

    private void applyMosaic() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        int block = (Integer) mosaicSpinner.getValue();
        canvas.applyAndRepaint("马赛克", () ->
                ImageEditorUtil.applyMosaic(canvas.getEditImage(), canvas.getEffectiveSelection(), block));
    }

    private void applyCanvasSize() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        int w = (Integer) canvasWSpinner.getValue();
        int h = (Integer) canvasHSpinner.getValue();
        canvas.applyAndRepaint("调整画布", () ->
                canvas.getLayerStack().resizeCanvas(w, h));
        syncSizeSpinners(canvas);
    }

    private void applyImageSize() {
        ImageEditorCanvas canvas = activeCanvas();
        if (canvas == null || canvas.getImage() == null) {
            return;
        }
        int w = (Integer) imageWSpinner.getValue();
        int h = (Integer) imageHSpinner.getValue();
        canvas.applyAndRepaint("缩放图片", () ->
                canvas.getLayerStack().resizeImageContent(w, h));
        syncSizeSpinners(canvas);
    }

    private void syncSizeSpinners(ImageEditorCanvas canvas) {
        BufferedImage img = canvas.getImage();
        if (img == null) {
            return;
        }
        canvasWSpinner.setValue(img.getWidth());
        canvasHSpinner.setValue(img.getHeight());
        imageWSpinner.setValue(img.getWidth());
        imageHSpinner.setValue(img.getHeight());
        updateImageInfo(canvas);
    }

    private void updateImageInfo(ImageEditorCanvas canvas) {
        if (activeCanvas() != canvas && canvas != null) {
            return;
        }
        imageInfoLabel.setText(buildImageInfoText(canvas));
    }

    private String buildImageInfoText(ImageEditorCanvas canvas) {
        if (canvas == null) {
            return "无图片";
        }
        BufferedImage img = canvas.getImage();
        if (img == null) {
            return "无图片";
        }
        String resolution = img.getWidth() + " × " + img.getHeight() + " px";
        return resolution + "  " + formatFileSizeKb(resolveImageSizeBytes(canvas, img));
    }

    private long resolveImageSizeBytes(ImageEditorCanvas canvas, BufferedImage img) {
        File file = fileMap.get(canvas);
        if (file != null && file.isFile() && file.exists()) {
            return file.length();
        }
        return (long) img.getWidth() * img.getHeight() * 4L;
    }

    private static String formatFileSizeKb(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return ((bytes + 512) / 1024) + " KB";
    }

    private void refreshHistoryWindow(ImageEditorCanvas canvas) {
        if (activeCanvas() == canvas || historyWindow.getBoundCanvas() == canvas) {
            historyWindow.refresh();
        }
    }

    private void applyPrefsToControls() {
        brushSpinner.setValue(prefs.brushSize);
        toleranceSpinner.setValue(prefs.fillTolerance);
        wandToleranceSpinner.setValue(prefs.magicWandTolerance);
        mosaicSpinner.setValue(prefs.mosaicBlockSize);
        qualitySlider.setValue(prefs.quality);
        currentColorSwatch.setBackground(prefs.foregroundColor);
        switch (prefs.saveFormat) {
            case "jpg" -> fmtJpg.setSelected(true);
            case "gif" -> fmtGif.setSelected(true);
            case "bmp" -> fmtBmp.setSelected(true);
            default -> fmtPng.setSelected(true);
        }
    }

    private void applyPrefsToCanvas(ImageEditorCanvas canvas) {
        canvas.setBrushSize(prefs.brushSize);
        canvas.setFillTolerance(prefs.fillTolerance);
        canvas.setMagicWandTolerance(prefs.magicWandTolerance);
        canvas.setForegroundColor(prefs.foregroundColor);
    }

    private void collectPrefsFromControls() {
        prefs.brushSize = (Integer) brushSpinner.getValue();
        prefs.fillTolerance = (Integer) toleranceSpinner.getValue();
        prefs.magicWandTolerance = (Integer) wandToleranceSpinner.getValue();
        prefs.mosaicBlockSize = (Integer) mosaicSpinner.getValue();
        prefs.quality = qualitySlider.getValue();
        if (fmtJpg.isSelected()) {
            prefs.saveFormat = "jpg";
        } else if (fmtGif.isSelected()) {
            prefs.saveFormat = "gif";
        } else if (fmtBmp.isSelected()) {
            prefs.saveFormat = "bmp";
        } else {
            prefs.saveFormat = "png";
        }
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(240, Integer.MAX_VALUE));
        return p;
    }

    private static JPanel row(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(240, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(56, 20));
        row.add(lbl, BorderLayout.WEST);
        field.setPreferredSize(new Dimension(120, 22));
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static JPanel btnRow(JButton... buttons) {
        JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
        row.setMaximumSize(new Dimension(240, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JButton b : buttons) {
            row.add(b);
        }
        return row;
    }

    private static JPanel wrap(JComponent c) {
        JPanel row = new JPanel(new BorderLayout());
        row.setMaximumSize(new Dimension(240, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(c, BorderLayout.CENTER);
        return row;
    }

    private static JButton compactBtn(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(2, 6, 2, 6));
        return b;
    }

    private static JSlider createSlider(int value) {
        return createSlider(value, 0, 255);
    }

    private static JSlider createSlider(int value, int min, int max) {
        JSlider s = new JSlider(min, max, value);
        s.setPaintTicks(false);
        s.setPaintLabels(false);
        return s;
    }

    private static JPanel compactSlider(String label, JSlider slider) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(240, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(36, 20));
        JLabel val = new JLabel(String.valueOf(slider.getValue()));
        val.setPreferredSize(new Dimension(28, 20));
        slider.addChangeListener(e -> val.setText(String.valueOf(slider.getValue())));
        row.add(lbl, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private static void resetSliders(JSlider... sliders) {
        for (JSlider s : sliders) {
            s.setValue(s.getMinimum() <= 0 && s.getMaximum() >= 0 ? 0 :
                    s.getMinimum() <= 255 && s.getMaximum() >= 255 ? 255 : 0);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
