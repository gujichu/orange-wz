package orange.wz.gui.component.canvas;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.ImagePreviewCollector;
import orange.wz.gui.utils.ImagePreviewData;
import orange.wz.provider.WzObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * 图片预览容器
 */
@Slf4j
public class ImagePreviewContainer extends JPanel {

    @Getter
    private ImagePreviewData previewData;
    private EditPane editPane;
    private final List<AnimationPreviewPanel> animationPanels = new ArrayList<>();

    /** 分页异步加载；切换页时取消上一个任务，避免并发解码撑爆堆 */
    private SwingWorker<ImagePreviewData, Void> paginationLoadWorker;

    public ImagePreviewContainer() {
        setLayout(new BorderLayout());
        setVisible(false);
    }

    public void setEditPane(EditPane editPane) {
        this.editPane = editPane;
    }

    /**
     * 设置预览数据
     */
    public void setPreviewData(ImagePreviewData data) {
        clear();

        this.previewData = data;

        if (data == null) {
            setVisible(false);
            return;
        }

        // 分页模式：先释放其它页的预览缓存（含帧像素），仅保留即将展示的当前页，避免多页堆叠占内存
        if (data.isPaginationActive() && editPane != null && data.getRootNode() != null) {
            editPane.getImagePreviewCache().retainOnlyPagedPreview(
                    data.getRootNode().getPath(), data.getPreviewPageIndex());
        }

        if (!data.hasContent() && !data.isPaginationActive()) {
            setVisible(false);
            return;
        }

        JPanel contentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));

        if (data.hasContent()) {
            for (ImagePreviewData.AnimationData anim : data.getAnimations()) {
                AnimationPreviewPanel panel = new AnimationPreviewPanel(anim, editPane);
                animationPanels.add(panel);
                contentPanel.add(panel);
            }

            for (ImagePreviewData.SingleImageData img : data.getSingleImages()) {
                SingleImagePreviewPanel panel = new SingleImagePreviewPanel(img, editPane);
                contentPanel.add(panel);
            }
        } else {
            contentPanel.add(new JLabel("本页无可预览内容"));
        }

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(30);
        verticalScrollBar.setBlockIncrement(100);
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        horizontalScrollBar.setUnitIncrement(30);
        horizontalScrollBar.setBlockIncrement(100);

        removeAll();

        if (data.isPaginationActive()) {
            JComponent toolbar = buildPaginationToolbar(data);
            add(toolbar, BorderLayout.NORTH);
        }

        add(scrollPane, BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();
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

        JLabel hint = new JLabel(
            String.format("  每页 %d 个子节点，共 %d 页", data.getPreviewPageSize(), total)
        );
        hint.setForeground(Color.GRAY);
        inner.add(hint);

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        int rowH = Math.max(32, inner.getPreferredSize().height);
        scroll.setPreferredSize(new Dimension(0, rowH + 6));
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
        String pagedKey = basePath + "#p" + pageIndex;

        // 命中缓存：直接换页（setPreviewData 内会先 clear 掉旧面板）
        ImagePreviewData cached = cache.get(pagedKey);
        if (cached != null) {
            cache.retainOnlyPagedPreview(basePath, pageIndex);
            editPane.getNodeForm().setPreviewData(cached);
            return;
        }

        if (paginationLoadWorker != null && !paginationLoadWorker.isDone()) {
            paginationLoadWorker.cancel(true);
        }

        int totalPages = previewData.getPreviewTotalPages();
        int pageSize = previewData.getPreviewPageSize();
        int childCount = previewData.getPreviewChildCountAtRoot();

        // 整路径预览条目清空（含当前页），避免 LRU 仍引用大块 ImagePreviewData
        cache.removePreviewGroup(basePath);

        // 立即拆掉当前页所有 AnimationPreviewPanel（flush 对齐缓冲），并丢弃仍引用帧像素的 previewData
        clear();

        ImagePreviewData stub = new ImagePreviewData();
        stub.setRootNode(root);
        stub.setPreviewPageIndex(pageIndex);
        stub.setPreviewTotalPages(totalPages);
        stub.setPreviewPageSize(pageSize);
        stub.setPreviewChildCountAtRoot(childCount);
        this.previewData = stub;

        removeAll();
        add(buildPaginationToolbar(stub), BorderLayout.NORTH);
        JPanel loadingWrap = new JPanel(new BorderLayout());
        loadingWrap.add(new JLabel("加载中...", SwingConstants.CENTER), BorderLayout.CENTER);
        add(new JScrollPane(loadingWrap), BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();

        System.gc();

        paginationLoadWorker = new SwingWorker<>() {
            @Override
            protected ImagePreviewData doInBackground() {
                return ImagePreviewCollector.collectPage(root, pageIndex);
            }

            @Override
            protected void done() {
                SwingWorker<ImagePreviewData, Void> self = this;
                try {
                    if (isCancelled()) {
                        return;
                    }
                    if (paginationLoadWorker != self) {
                        return;
                    }
                    ImagePreviewData d = get();
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
        clear();
        removeAll();
        add(buildPaginationToolbar(stub), BorderLayout.NORTH);
        String text = cause instanceof OutOfMemoryError
                ? "内存不足，无法解码本页。可尝试增大 JVM 堆内存或减少并发预览。"
                : "加载失败: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
        JPanel msg = new JPanel(new BorderLayout());
        msg.add(new JLabel(text, SwingConstants.CENTER), BorderLayout.CENTER);
        add(new JScrollPane(msg), BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * 清理资源
     */
    private void clear() {
        for (AnimationPreviewPanel panel : animationPanels) {
            panel.dispose();
        }
        animationPanels.clear();
        removeAll();
    }

    /**
     * 隐藏预览
     */
    public void hidePreview() {
        clear();
        this.previewData = null;
        setVisible(false);
    }
}
