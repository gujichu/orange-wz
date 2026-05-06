package orange.wz.gui.component.canvas;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.AnimationPreviewConfigIni;
import orange.wz.gui.utils.AnimationPreviewOptions;
import orange.wz.gui.utils.ImagePreviewCollector;
import orange.wz.gui.utils.ImagePreviewData;
import orange.wz.provider.WzObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;

/**
 * 图片预览容器
 */
@Slf4j
public class ImagePreviewContainer extends JPanel {

    private static final String ALL_PREVIEW_NODES = "全部节点";

    @Getter
    private ImagePreviewData previewData;
    private EditPane editPane;

    /**
     * 当前页所有预览格（动画 + 单图），节点下拉仅切换可见性，不反复 new 面板，避免重复分配对齐帧缓存。
     */
    private final List<PreviewSlot> previewSlots = new ArrayList<>();
    private JPanel previewContentPanel;
    private JScrollPane previewScrollPane;
    private JLabel filterEmptyHint;

    private static final class PreviewSlot {
        final String nodeName;
        final JComponent component;

        PreviewSlot(String nodeName, JComponent component) {
            this.nodeName = nodeName != null ? nodeName : "";
            this.component = component;
        }
    }

    private final JPanel northRow = new JPanel(new BorderLayout());
    private final JPanel centerHost = new JPanel(new BorderLayout());
    private final JCheckBox cacheCheckbox = new JCheckBox("预览缓存");
    private final JCheckBox englishCheckbox = new JCheckBox("英文节点");
    private final JCheckBox numericCheckbox = new JCheckBox("数字节点");
    private final JCheckBox iconCheckbox = new JCheckBox("图标节点");
    private final JPanel optionsStripPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    private final JComboBox<String> previewNodeCombo = new JComboBox<>();
    private boolean updatingPreviewNodeCombo;
    private String lastPreviewNodeSelection = ALL_PREVIEW_NODES;

    /** 分页异步加载；切换页时取消上一个任务，避免并发解码撑爆堆 */
    private SwingWorker<ImagePreviewData, Void> paginationLoadWorker;

    public ImagePreviewContainer() {
        setLayout(new BorderLayout());
        initOptionsStripPanel();
        northRow.setLayout(new BoxLayout(northRow, BoxLayout.X_AXIS));
        northRow.add(Box.createHorizontalGlue());
        optionsStripPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        northRow.add(optionsStripPanel);
        add(northRow, BorderLayout.NORTH);
        add(centerHost, BorderLayout.CENTER);
        setVisible(false);
    }

    public void setEditPane(EditPane editPane) {
        this.editPane = editPane;
    }

    private void initOptionsStripPanel() {
        syncToolbarFromConfig();
        ItemListener persist = e -> {
            if (e.getStateChange() != ItemEvent.SELECTED && e.getStateChange() != ItemEvent.DESELECTED) {
                return;
            }
            persistToolbarToIni();
        };
        cacheCheckbox.addItemListener(persist);
        englishCheckbox.addItemListener(persist);
        numericCheckbox.addItemListener(persist);
        iconCheckbox.addItemListener(persist);
        previewNodeCombo.setPrototypeDisplayValue("MMMM");
        previewNodeCombo.setToolTipText("按名称筛选当前页预览项");
        int comboH = Math.max(22, previewNodeCombo.getPreferredSize().height);
        previewNodeCombo.setPreferredSize(new Dimension(120, comboH));
        previewNodeCombo.addActionListener(e -> {
            if (updatingPreviewNodeCombo) {
                return;
            }
            Object sel = previewNodeCombo.getSelectedItem();
            lastPreviewNodeSelection = sel != null ? sel.toString() : ALL_PREVIEW_NODES;
            applyPreviewNodeFilterVisibility();
        });
        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> {
            if (editPane != null) {
                editPane.getNodeForm().refreshPreviewFromToolbar();
            }
        });
        optionsStripPanel.add(previewNodeCombo);
        optionsStripPanel.add(cacheCheckbox);
        optionsStripPanel.add(englishCheckbox);
        optionsStripPanel.add(numericCheckbox);
        optionsStripPanel.add(iconCheckbox);
        optionsStripPanel.add(refreshBtn);
        resetPreviewNodeCombo();
    }

    private void syncToolbarFromConfig() {
        AnimationPreviewOptions o = AnimationPreviewConfigIni.getOptions();
        cacheCheckbox.setSelected(o.isPreviewCacheEnabled());
        englishCheckbox.setSelected(o.isIncludeEnglishNamed());
        numericCheckbox.setSelected(o.isIncludeNumericNamed());
        iconCheckbox.setSelected(o.isIncludeIconNamed());
    }

    private void persistToolbarToIni() {
        AnimationPreviewOptions o = AnimationPreviewConfigIni.getOptions().copy();
        o.setPreviewCacheEnabled(cacheCheckbox.isSelected());
        o.setIncludeEnglishNamed(englishCheckbox.isSelected());
        o.setIncludeNumericNamed(numericCheckbox.isSelected());
        o.setIncludeIconNamed(iconCheckbox.isSelected());
        AnimationPreviewConfigIni.updateOptions(o);
    }

    /**
     * 设置预览数据
     */
    public void setPreviewData(ImagePreviewData data) {
        clearPreviewContent();

        this.previewData = data;

        if (data == null) {
            resetNorthToOptionsOnly();
            resetPreviewNodeCombo();
            setVisible(false);
            return;
        }

        syncToolbarFromConfig();

        // 分页模式：先释放其它页的预览缓存（含帧像素），仅保留即将展示的当前页，避免多页堆叠占内存
        if (data.isPaginationActive() && editPane != null && data.getRootNode() != null) {
            String fs = data.getPreviewFilterSuffix() != null ? data.getPreviewFilterSuffix() : "";
            editPane.getImagePreviewCache().retainOnlyPagedPreview(
                    data.getRootNode().getPath(), data.getPreviewPageIndex(), fs);
        }

        if (!data.hasContent() && !data.isPaginationActive()) {
            if (data.getRootNode() != null) {
                rebuildNorthRow(null);
                repopulatePreviewNodeComboAndRebuild(data);
                setVisible(true);
                revalidate();
                repaint();
                return;
            }
            resetNorthToOptionsOnly();
            resetPreviewNodeCombo();
            setVisible(false);
            return;
        }

        rebuildNorthRow(data.isPaginationActive() ? buildPaginationToolbar(data) : null);
        repopulatePreviewNodeComboAndRebuild(data);
        setVisible(true);
        revalidate();
        repaint();
    }

    private void resetPreviewNodeCombo() {
        updatingPreviewNodeCombo = true;
        try {
            previewNodeCombo.setModel(new DefaultComboBoxModel<>(new String[]{ALL_PREVIEW_NODES}));
            previewNodeCombo.setSelectedIndex(0);
            previewNodeCombo.setEnabled(false);
        } finally {
            updatingPreviewNodeCombo = false;
        }
        lastPreviewNodeSelection = ALL_PREVIEW_NODES;
    }

    /**
     * 用当前 {@link #previewData} 中的名称填充下拉框，并按选项重建中间预览区。
     */
    private void repopulatePreviewNodeComboAndRebuild(ImagePreviewData data) {
        updatingPreviewNodeCombo = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement(ALL_PREVIEW_NODES);
            if (data != null) {
                TreeSet<String> names = new TreeSet<>(Comparator.comparing(String::toLowerCase));
                for (ImagePreviewData.AnimationData anim : data.getAnimations()) {
                    if (anim.getName() != null && !anim.getName().isEmpty()) {
                        names.add(anim.getName());
                    }
                }
                for (ImagePreviewData.SingleImageData img : data.getSingleImages()) {
                    if (img.getName() != null && !img.getName().isEmpty()) {
                        names.add(img.getName());
                    }
                }
                for (String n : names) {
                    model.addElement(n);
                }
            }
            previewNodeCombo.setModel(model);
            previewNodeCombo.setEnabled(data != null && data.hasContent());
            if (model.getIndexOf(lastPreviewNodeSelection) >= 0) {
                previewNodeCombo.setSelectedItem(lastPreviewNodeSelection);
            } else {
                previewNodeCombo.setSelectedItem(ALL_PREVIEW_NODES);
                lastPreviewNodeSelection = ALL_PREVIEW_NODES;
            }
        } finally {
            updatingPreviewNodeCombo = false;
        }
        rebuildPreviewSurfaceFull();
    }

    /**
     * 根据当前 {@link #previewData} 构建全部预览面板（仅在一次加载/刷新时调用）。
     */
    private void rebuildPreviewSurfaceFull() {
        if (previewData == null) {
            return;
        }
        disposePreviewSlots();
        previewSlots.clear();
        filterEmptyHint = null;
        previewContentPanel = null;
        previewScrollPane = null;
        centerHost.removeAll();

        previewContentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        previewScrollPane = new JScrollPane(previewContentPanel);
        previewScrollPane.setBorder(BorderFactory.createEmptyBorder());
        previewScrollPane.getVerticalScrollBar().setUnitIncrement(30);
        previewScrollPane.getVerticalScrollBar().setBlockIncrement(100);
        previewScrollPane.getHorizontalScrollBar().setUnitIncrement(30);
        previewScrollPane.getHorizontalScrollBar().setBlockIncrement(100);

        if (!previewData.hasContent()) {
            String msg;
            if (previewData.isPaginationActive()) {
                msg = "本页无可预览内容";
            } else if (previewData.getRootNode() != null) {
                msg = "当前过滤条件下无可预览内容";
            } else {
                msg = "本页无可预览内容";
            }
            previewContentPanel.add(new JLabel(msg));
            centerHost.add(previewScrollPane, BorderLayout.CENTER);
            revalidate();
            repaint();
            return;
        }

        for (ImagePreviewData.AnimationData anim : previewData.getAnimations()) {
            AnimationPreviewPanel panel = new AnimationPreviewPanel(anim, editPane);
            String nm = anim.getName() != null ? anim.getName() : "";
            previewSlots.add(new PreviewSlot(nm, panel));
            previewContentPanel.add(panel);
        }
        for (ImagePreviewData.SingleImageData img : previewData.getSingleImages()) {
            SingleImagePreviewPanel panel = new SingleImagePreviewPanel(img, editPane);
            String nm = img.getName() != null ? img.getName() : "";
            previewSlots.add(new PreviewSlot(nm, panel));
            previewContentPanel.add(panel);
        }

        centerHost.add(previewScrollPane, BorderLayout.CENTER);
        applyPreviewNodeFilterVisibility();
        revalidate();
        repaint();
    }

    /**
     * 节点下拉框：仅显示/隐藏已有面板，不销毁重建（WrapLayout 不计入不可见组件占位）。
     */
    private void applyPreviewNodeFilterVisibility() {
        if (previewContentPanel == null || previewSlots.isEmpty()) {
            return;
        }
        if (filterEmptyHint != null) {
            previewContentPanel.remove(filterEmptyHint);
            filterEmptyHint = null;
        }

        Object sel = previewNodeCombo.getSelectedItem();
        String nameFilter = (sel == null || ALL_PREVIEW_NODES.equals(sel.toString())) ? null : sel.toString();

        int visibleCount = 0;
        if (nameFilter == null) {
            for (PreviewSlot slot : previewSlots) {
                slot.component.setVisible(true);
                visibleCount++;
            }
        } else {
            for (PreviewSlot slot : previewSlots) {
                boolean show = nameFilter.equals(slot.nodeName);
                slot.component.setVisible(show);
                if (show) {
                    visibleCount++;
                }
            }
            if (visibleCount == 0) {
                filterEmptyHint = new JLabel("无名为 \"" + nameFilter + "\" 的预览项");
                previewContentPanel.add(filterEmptyHint);
            }
        }

        previewContentPanel.revalidate();
        previewContentPanel.repaint();
        previewScrollPane.revalidate();
        previewScrollPane.repaint();
    }

    private void disposePreviewSlots() {
        for (PreviewSlot slot : previewSlots) {
            if (slot.component instanceof AnimationPreviewPanel ap) {
                ap.dispose();
            }
        }
    }

    /**
     * 顶栏：左侧分页（可选），中间弹性空白，右侧选项条。避免 BorderLayout 下 WEST 被 EAST 挤没。
     */
    private void rebuildNorthRow(JComponent westPagination) {
        northRow.removeAll();
        northRow.setLayout(new BoxLayout(northRow, BoxLayout.X_AXIS));
        if (westPagination != null) {
            westPagination.setAlignmentY(Component.TOP_ALIGNMENT);
            northRow.add(westPagination);
        }
        northRow.add(Box.createHorizontalGlue());
        optionsStripPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        northRow.add(optionsStripPanel);
        revalidate();
    }

    private void resetNorthToOptionsOnly() {
        northRow.removeAll();
        northRow.setLayout(new BoxLayout(northRow, BoxLayout.X_AXIS));
        northRow.add(Box.createHorizontalGlue());
        optionsStripPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        northRow.add(optionsStripPanel);
    }

    private JComponent buildPaginationToolbar(ImagePreviewData data) {
        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        int total = data.getPreviewTotalPages();
        int current = data.getPreviewPageIndex();

        JButton prev = new JButton("上一页");
        prev.setEnabled(current > 0);
        prev.addActionListener(e -> loadPreviewPage(current - 1));
        inner.add(prev);

        for (int p = 0; p < total; p++) {
            int pageIdx = p;
            JButton pageBtn = new JButton(String.valueOf(p + 1));
            if (p == current) {
                pageBtn.setEnabled(false);
            } else {
                pageBtn.addActionListener(e -> loadPreviewPage(pageIdx));
            }
            inner.add(pageBtn);
        }

        JButton next = new JButton("下一页");
        next.setEnabled(current < total - 1);
        next.addActionListener(e -> loadPreviewPage(current + 1));
        inner.add(next);

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        int rowH = Math.max(32, inner.getPreferredSize().height);
        // 宽度不能为 0，否则在 BoxLayout / 窄面板下分页条会被挤成不可见
        int prefW = Math.min(1200, Math.max(240, inner.getPreferredSize().width + 24));
        scroll.setPreferredSize(new Dimension(prefW, rowH + 6));
        scroll.setMinimumSize(new Dimension(160, rowH + 6));
        return scroll;
    }

    private void loadPreviewPage(int pageIndex) {
        if (previewData == null || editPane == null || previewData.getRootNode() == null) {
            return;
        }
        if (!previewData.isPaginationActive()) {
            return;
        }

        WzObject root = previewData.getRootNode();
        String basePath = root.getPath();
        var cache = editPane.getImagePreviewCache();
        String filterSuffix = AnimationPreviewConfigIni.getOptions().previewFilterCacheSuffix();
        String pagedKey = basePath + "#p" + pageIndex + filterSuffix;

        // 命中缓存：直接换页（setPreviewData 内会先 clear 掉旧面板）
        ImagePreviewData cached = cache.get(pagedKey);
        if (cached != null) {
            cache.retainOnlyPagedPreview(basePath, pageIndex, filterSuffix);
            editPane.getNodeForm().setPreviewData(cached);
            return;
        }

        if (paginationLoadWorker != null && !paginationLoadWorker.isDone()) {
            paginationLoadWorker.cancel(true);
        }

        int totalPages = previewData.getPreviewTotalPages();
        int pageSize = previewData.getPreviewPageSize();
        int childCount = previewData.getPreviewChildCountAtRoot();

        // 先拆 UI、再换掉 previewData 引用，最后清缓存并释放位图，避免 discard 时仍指向当前页数据
        clearPreviewContent();

        ImagePreviewData stub = new ImagePreviewData();
        stub.setRootNode(root);
        stub.setPreviewPageIndex(pageIndex);
        stub.setPreviewTotalPages(totalPages);
        stub.setPreviewPageSize(pageSize);
        stub.setPreviewChildCountAtRoot(childCount);
        this.previewData = stub;

        cache.removePreviewGroup(basePath);

        rebuildNorthRow(buildPaginationToolbar(stub));
        JPanel loadingWrap = new JPanel(new BorderLayout());
        loadingWrap.add(new JLabel("加载中...", SwingConstants.CENTER), BorderLayout.CENTER);
        centerHost.add(new JScrollPane(loadingWrap), BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();

        System.gc();

        paginationLoadWorker = new SwingWorker<>() {
            @Override
            protected ImagePreviewData doInBackground() {
                return ImagePreviewCollector.collectPage(root, pageIndex, AnimationPreviewConfigIni.getOptions());
            }

            @Override
            protected void done() {
                SwingWorker<ImagePreviewData, Void> self = this;
                try {
                    ImagePreviewData d = get();
                    if (isCancelled() || paginationLoadWorker != self) {
                        if (d != null) {
                            d.discardPixelData();
                        }
                        return;
                    }
                    editPane.getImagePreviewCache().putPreview(d);
                    editPane.getNodeForm().setPreviewData(d);
                } catch (CancellationException ex) {
                    log.debug("预览分页加载已取消");
                } catch (Exception ex) {
                    Throwable cause = ex;
                    if (ex instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) {
                        cause = ee.getCause();
                    }
                    if (cause instanceof CancellationException) {
                        log.debug("预览分页加载已取消");
                        return;
                    }
                    if (paginationLoadWorker != self) {
                        return;
                    }
                    if (cause instanceof OutOfMemoryError) {
                        log.error("预览分页 OOM: {}", basePath, cause);
                        System.gc();
                    } else {
                        log.error("加载预览分页失败", ex);
                    }
                    ImagePreviewData errStub = new ImagePreviewData();
                    errStub.setRootNode(root);
                    errStub.setPreviewPageIndex(pageIndex);
                    errStub.setPreviewTotalPages(totalPages);
                    errStub.setPreviewPageSize(pageSize);
                    errStub.setPreviewChildCountAtRoot(childCount);
                    showPaginationLoadFailure(errStub, cause);
                } finally {
                    if (paginationLoadWorker == self) {
                        paginationLoadWorker = null;
                    }
                }
            }
        };
        paginationLoadWorker.execute();
    }

    /**
     * 加载失败时仍保留分页条，中间提示错误（避免卡在「加载中」）
     */
    private void showPaginationLoadFailure(ImagePreviewData stub, Throwable cause) {
        this.previewData = stub;
        clearPreviewContent();
        rebuildNorthRow(buildPaginationToolbar(stub));
        String text = cause instanceof OutOfMemoryError
                ? "内存不足，无法解码本页。可尝试增大 JVM 堆内存或减少并发预览。"
                : "加载失败: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
        JPanel msg = new JPanel(new BorderLayout());
        msg.add(new JLabel(text, SwingConstants.CENTER), BorderLayout.CENTER);
        centerHost.add(new JScrollPane(msg), BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * 清理资源（仅预览内容区，保留顶部配置条）
     */
    private void clearPreviewContent() {
        disposePreviewSlots();
        previewSlots.clear();
        filterEmptyHint = null;
        previewContentPanel = null;
        previewScrollPane = null;
        centerHost.removeAll();
    }

    /**
     * 隐藏预览
     */
    public void hidePreview() {
        cancelPendingPreviewLoads();
        clearPreviewContent();
        resetNorthToOptionsOnly();
        resetPreviewNodeCombo();
        this.previewData = null;
        setVisible(false);
    }

    /**
     * 取消分页后台解码，避免与手动内存回收或其它操作并发占堆。
     */
    public void cancelPendingPreviewLoads() {
        SwingWorker<ImagePreviewData, Void> w = paginationLoadWorker;
        if (w != null && !w.isDone()) {
            w.cancel(true);
        }
        paginationLoadWorker = null;
    }
}
