package orange.wz.gui.component.form.impl;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.dialog.VideoExportImagesDialog;
import orange.wz.gui.component.dialog.VideoGenerateFrameNodesFormatDialog;
import orange.wz.gui.component.form.data.VideoFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.component.panel.ImagePanel;
import orange.wz.gui.utils.JMessageUtil;
import orange.wz.gui.video.CanvasVideoFfmpegDecoder;
import orange.wz.gui.video.McvFileParser;
import orange.wz.gui.video.McvHeader;
import orange.wz.gui.video.VideoArgbFormatConverter;
import orange.wz.gui.video.VideoImageBitDepth;
import orange.wz.gui.video.VideoImageSequenceExporter;
import orange.wz.gui.video.VideoPlaybackConstants;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzVectorProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzVideoProperty;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
public class VideoForm extends AbstractValueForm {

    private final ImagePanel imagePanel = new ImagePanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JScrollPane imageScroll;
    private final JSlider zoomSlider;
    private final JLabel zoomValueLabel;
    private final JButton btnPlay = new JButton("播放");
    private final JButton btnExportVideo = new JButton("导出视频");
    private final JButton btnExportImages = new JButton("导出图片集");
    private final JButton btnGenerateFrameNodes = new JButton("生成子节点");

    private static final int ORIGIN_REF_WIDTH = 1456;
    private static final int ORIGIN_REF_HEIGHT = 860;
    private static final int ORIGIN_REF_X = 720;
    private static final int ORIGIN_REF_Y = 500;

    /** 仅用于「播放」时的 FFmpeg 全量解码 */
    private SwingWorker<CanvasVideoFfmpegDecoder.DecodedVideo, Void> loadWorker;
    private Timer animTimer;
    private List<BufferedImage> frames;
    private int[] delayMillis;
    private int frameIndex;
    private boolean playing;
    private byte[] lastMcvBytes;

    /** 由 {@link McvFileParser} 解析得到，无需解码整段视频即可显示帧数与分辨率 */
    private boolean metaReady;
    private int cachedFrameCount;
    private int cachedWidth;
    private int cachedHeight;

    public VideoForm() {
        super();
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JPanel north = new JPanel(new BorderLayout());
        north.add(statusLabel, BorderLayout.WEST);
        center.add(north, BorderLayout.NORTH);

        imagePanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JPanel viewportHost = new JPanel(new GridBagLayout());
        GridBagConstraints gbcHost = new GridBagConstraints();
        gbcHost.gridx = 0;
        gbcHost.gridy = 0;
        gbcHost.weightx = 1;
        gbcHost.weighty = 1;
        gbcHost.anchor = GridBagConstraints.CENTER;
        viewportHost.add(imagePanel, gbcHost);
        imageScroll = new JScrollPane(viewportHost,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        imageScroll.setPreferredSize(new Dimension(360, 300));
        center.add(imageScroll, BorderLayout.CENTER);

        zoomSlider = new JSlider(10, 300, 40);
        zoomSlider.setMajorTickSpacing(50);
        zoomSlider.setMinorTickSpacing(10);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setPaintLabels(true);
        zoomValueLabel = new JLabel("40%");
        zoomSlider.addChangeListener(e -> {
            int pct = zoomSlider.getValue();
            zoomValueLabel.setText(pct + "%");
            imagePanel.setZoomFactor(pct / 100.0);
            imageScroll.revalidate();
            imageScroll.repaint();
        });
        JPanel zoomRow = new JPanel(new BorderLayout(8, 0));
        zoomRow.add(new JLabel("显示:"), BorderLayout.WEST);
        zoomRow.add(zoomSlider, BorderLayout.CENTER);
        zoomRow.add(zoomValueLabel, BorderLayout.EAST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnPlay.setEnabled(false);
        btnExportVideo.setEnabled(false);
        btnExportImages.setEnabled(false);
        btnGenerateFrameNodes.setEnabled(false);
        btnPlay.addActionListener(e -> togglePlay());
        btnExportVideo.addActionListener(e -> exportVideo());
        btnExportImages.addActionListener(e -> exportImages());
        btnGenerateFrameNodes.addActionListener(e -> generateFrameImageSiblingNodes());
        btnRow.add(btnPlay);
        btnRow.add(btnExportVideo);
        btnRow.add(btnExportImages);
        btnRow.add(btnGenerateFrameNodes);

        JPanel southWrap = new JPanel(new BorderLayout(0, 6));
        southWrap.add(zoomRow, BorderLayout.NORTH);
        southWrap.add(btnRow, BorderLayout.SOUTH);
        center.add(southWrap, BorderLayout.SOUTH);

        valuePane.add(center, BorderLayout.CENTER);

        imagePanel.setZoomFactor(0.4);

        animTimer = new Timer(VideoPlaybackConstants.FRAME_INTERVAL_MS, e -> onAnimTick());
        animTimer.setRepeats(true);
        animTimer.setCoalesce(false);
    }

    public void setData(String name, String type, WzVideoProperty property, EditPane editPane) {
        applyVideoPayload(name, type, property.getBytes(false), property, editPane);
    }

    protected void applyVideoPayload(String name, String type, byte[] mcvBytes, WzObject wzObject, EditPane editPane) {
        super.setData(name, type, wzObject, editPane);
        discardDecodedFramesAndTimer();
        metaReady = false;
        cachedFrameCount = 0;
        cachedWidth = 0;
        cachedHeight = 0;
        lastMcvBytes = mcvBytes;

        if (mcvBytes == null || mcvBytes.length == 0) {
            statusLabel.setText("无视频数据");
            setVideoActionButtonsEnabled(false);
            imageScroll.revalidate();
            imageScroll.repaint();
            return;
        }

        try {
            McvHeader hdr = McvFileParser.parse(mcvBytes);
            metaReady = true;
            cachedFrameCount = hdr.getFrameCount();
            cachedWidth = hdr.getWidth();
            cachedHeight = hdr.getHeight();
        } catch (Exception ex) {
            log.warn("MCV 头解析失败", ex);
            metaReady = false;
            statusLabel.setText("视频头解析失败");
            JMessageUtil.error("视频头解析失败: " + ex.getMessage());
            setVideoActionButtonsEnabled(false);
            imageScroll.revalidate();
            imageScroll.repaint();
            return;
        }

        if (cachedFrameCount <= 0) {
            statusLabel.setText("无帧");
            setVideoActionButtonsEnabled(false);
        } else {
            statusLabel.setText(cachedFrameCount + " 帧 · " + cachedWidth + "×" + cachedHeight + " · 点击「播放」解码预览");
            setVideoActionButtonsEnabled(true);
        }
        imageScroll.revalidate();
        imageScroll.repaint();
    }

    private void setVideoActionButtonsEnabled(boolean ok) {
        btnPlay.setEnabled(ok);
        btnExportVideo.setEnabled(ok);
        btnExportImages.setEnabled(ok);
        btnGenerateFrameNodes.setEnabled(ok);
    }

    /**
     * 丢弃 FFmpeg 解码得到的帧缓存（保留 {@link #lastMcvBytes}，可再次播放或导出时按需解码）。
     */
    private void releaseDecodedVideoMemory() {
        discardDecodedFramesAndTimer();
        refreshVideoStatusHint();
    }

    private void discardDecodedFramesAndTimer() {
        playing = false;
        animTimer.stop();
        btnPlay.setText("播放");
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        flushFrameList(frames);
        frames = null;
        delayMillis = null;
        frameIndex = 0;
        imagePanel.setImage(null);
    }

    private void refreshVideoStatusHint() {
        if (metaReady && cachedFrameCount > 0) {
            statusLabel.setText(cachedFrameCount + " 帧 · " + cachedWidth + "×" + cachedHeight + " · 点击「播放」解码预览");
        } else if (metaReady) {
            statusLabel.setText("无帧");
        } else {
            statusLabel.setText(" ");
        }
        imageScroll.revalidate();
        imageScroll.repaint();
    }

    private static void flushFrameList(List<BufferedImage> list) {
        if (list == null) {
            return;
        }
        for (BufferedImage bi : list) {
            if (bi != null) {
                bi.flush();
            }
        }
    }

    private void onAnimTick() {
        if (!playing || frames == null || frames.isEmpty()) {
            return;
        }
        frameIndex++;
        if (frameIndex >= frames.size()) {
            stopPlayback();
            return;
        }
        imagePanel.setImage(frames.get(frameIndex));
    }

    private void togglePlay() {
        if (playing) {
            stopPlayback();
            return;
        }
        if (lastMcvBytes == null || lastMcvBytes.length == 0) {
            return;
        }
        if (frames != null && !frames.isEmpty()) {
            startAnimatingDecodedFrames();
            return;
        }
        if (loadWorker != null && !loadWorker.isDone()) {
            return;
        }
        statusLabel.setText("解码中…");
        setVideoActionButtonsEnabled(false);
        loadWorker = new SwingWorker<>() {
            @Override
            protected CanvasVideoFfmpegDecoder.DecodedVideo doInBackground() throws Exception {
                return CanvasVideoFfmpegDecoder.decode(lastMcvBytes);
            }

            @Override
            protected void done() {
                if (metaReady && cachedFrameCount > 0) {
                    setVideoActionButtonsEnabled(true);
                }
                if (isCancelled()) {
                    refreshVideoStatusHint();
                    return;
                }
                try {
                    CanvasVideoFfmpegDecoder.DecodedVideo v = get();
                    frames = v.frames();
                    delayMillis = v.delayMillis();
                    if (frames == null || frames.isEmpty()) {
                        statusLabel.setText("无帧");
                        releaseDecodedVideoMemory();
                        return;
                    }
                    statusLabel.setText(frames.size() + " 帧 · " + cachedWidth + "×" + cachedHeight + " · 预览中");
                    startAnimatingDecodedFrames();
                } catch (InterruptedException | ExecutionException ex) {
                    log.error("视频解码失败", ex);
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("解码失败");
                    JMessageUtil.error("视频解码失败: " + c.getMessage());
                    refreshVideoStatusHint();
                }
            }
        };
        loadWorker.execute();
    }

    private void startAnimatingDecodedFrames() {
        playing = true;
        frameIndex = 0;
        imagePanel.setImage(frames.getFirst());
        btnPlay.setText("暂停");
        animTimer.stop();
        animTimer.setInitialDelay(VideoPlaybackConstants.FRAME_INTERVAL_MS);
        animTimer.setDelay(VideoPlaybackConstants.FRAME_INTERVAL_MS);
        animTimer.start();
    }

    private void stopPlayback() {
        releaseDecodedVideoMemory();
    }

    private void exportVideo() {
        if (lastMcvBytes == null || lastMcvBytes.length == 0 || !metaReady || cachedFrameCount <= 0) {
            return;
        }
        SystemFileChooser ch = new SystemFileChooser();
        ch.setDialogTitle("导出视频（每帧 " + VideoPlaybackConstants.FRAME_INTERVAL_MS + "ms）");
        ch.setSelectedFile(new File(safeFileName(nameInput.getText()) + ".webm"));
        ch.addChoosableFileFilter(new SystemFileChooser.FileNameExtensionFilter("WebM (*.webm)", "webm"));
        ch.addChoosableFileFilter(new SystemFileChooser.FileNameExtensionFilter("MP4 (*.mp4)", "mp4"));
        if (ch.showSaveDialog(valuePane) != SystemFileChooser.APPROVE_OPTION) {
            return;
        }
        File out = ch.getSelectedFile();
        if (out == null) {
            return;
        }
        String name = out.getName().toLowerCase();
        boolean mp4;
        if (name.endsWith(".mp4")) {
            mp4 = true;
        } else if (name.endsWith(".webm")) {
            mp4 = false;
        } else {
            var filter = ch.getFileFilter();
            if (filter != null && filter.getDescription().toLowerCase().contains("mp4")) {
                mp4 = true;
                out = new File(out.getAbsolutePath() + ".mp4");
            } else {
                mp4 = false;
                out = new File(out.getAbsolutePath() + ".webm");
            }
        }
        File fout = out;
        boolean asMp4 = mp4;
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<BufferedImage> useFrames = frames;
                if (useFrames == null || useFrames.isEmpty()) {
                    CanvasVideoFfmpegDecoder.DecodedVideo v = CanvasVideoFfmpegDecoder.decode(lastMcvBytes);
                    useFrames = v.frames();
                    try {
                        CanvasVideoFfmpegDecoder.exportVideoFromFrames(useFrames, fout.toPath(), asMp4);
                    } finally {
                        flushFrameList(useFrames);
                    }
                } else {
                    CanvasVideoFfmpegDecoder.exportVideoFromFrames(useFrames, fout.toPath(), asMp4);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JMessageUtil.info("已导出:\n" + fout.getAbsolutePath());
                } catch (Exception ex) {
                    log.error("导出视频失败", ex);
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JMessageUtil.error("导出视频失败: " + c.getMessage());
                } finally {
                    releaseDecodedVideoMemory();
                }
            }
        };
        w.execute();
    }

    private void exportImages() {
        if (lastMcvBytes == null || lastMcvBytes.length == 0 || !metaReady || cachedFrameCount <= 0) {
            return;
        }
        int dialogFrameCount = (frames != null && !frames.isEmpty()) ? frames.size() : cachedFrameCount;
        Window w = SwingUtilities.getWindowAncestor(valuePane);
        VideoExportImagesDialog dlg = new VideoExportImagesDialog(w, Math.max(1, dialogFrameCount));
        VideoExportImagesDialog.Result r = dlg.openAndGetResult();
        if (r == null) {
            return;
        }
        Path dir = r.getDirectory();
        if (dir == null) {
            SystemFileChooser ch = new SystemFileChooser();
            ch.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
            ch.setDialogTitle("选择导出目录");
            if (ch.showOpenDialog(valuePane) != SystemFileChooser.APPROVE_OPTION) {
                return;
            }
            File f = ch.getSelectedFile();
            if (f == null) {
                return;
            }
            dir = f.toPath();
        }
        String prefix = safeFileName(nameInput.getText());
        Path dirFinal = dir;
        VideoExportImagesDialog.ImageExportFormat fmt = r.getFormat();
        VideoImageBitDepth depth = r.getBitDepth();
        List<Integer> indices = r.getFrameIndices();
        BiConsumer<Integer, Integer> reportProgress = (done, total) -> SwingUtilities.invokeLater(() -> {
            MainFrame mf = MainFrame.getInstance();
            if (mf != null) {
                mf.updateProgress(done, total);
            }
        });

        SwingWorker<VideoImageSequenceExporter.ExportStats, Void> worker = new SwingWorker<>() {
            @Override
            protected VideoImageSequenceExporter.ExportStats doInBackground() throws Exception {
                List<BufferedImage> localFrames = frames;
                int[] localDelays = delayMillis;
                boolean ownedDecode = false;
                if (localFrames == null || localFrames.isEmpty()) {
                    CanvasVideoFfmpegDecoder.DecodedVideo v = CanvasVideoFfmpegDecoder.decode(lastMcvBytes);
                    localFrames = v.frames();
                    localDelays = v.delayMillis();
                    ownedDecode = true;
                }
                final int gifProgressTotal = localFrames.size() * 2 + 1;
                final int barTotal = switch (fmt) {
                    case PNG, JPG -> Math.max(1, indices.size());
                    case GIF -> Math.max(1, gifProgressTotal);
                    default -> 1;
                };
                try {
                    SwingUtilities.invokeLater(() -> {
                        MainFrame mf = MainFrame.getInstance();
                        if (mf != null) {
                            mf.updateProgress(0, barTotal);
                        }
                    });
                    Files.createDirectories(dirFinal);
                    return switch (fmt) {
                        case PNG -> exportPngJpg(localFrames, dirFinal, prefix, "png", indices, depth, reportProgress);
                        case JPG -> exportPngJpg(localFrames, dirFinal, prefix, "jpg", indices, depth, reportProgress);
                        case GIF -> exportGifWithStats(localFrames, localDelays, dirFinal, prefix, depth, gifProgressTotal, reportProgress);
                        default -> new VideoImageSequenceExporter.ExportStats(0, 0, List.of());
                    };
                } finally {
                    if (ownedDecode) {
                        flushFrameList(localFrames);
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    VideoImageSequenceExporter.ExportStats st = get();
                    JMessageUtil.info(buildExportImageReport(st, dirFinal));
                } catch (Exception ex) {
                    log.error("导出图片失败", ex);
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JMessageUtil.error("导出图片失败: " + c.getMessage());
                } finally {
                    releaseDecodedVideoMemory();
                    SwingUtilities.invokeLater(() -> {
                        MainFrame mf = MainFrame.getInstance();
                        if (mf != null) {
                            mf.resetProgressBar();
                        }
                    });
                }
            }
        };
        worker.execute();
    }

    /**
     * 在左侧树当前选中的视频节点之<strong>同级</strong>（同一父节点下）按帧数插入 Canvas 图片节点，名称 0～n-1，
     * 每张格式由对话框选择，子属性 delay / origin / z。
     */
    private void generateFrameImageSiblingNodes() {
        if (lastMcvBytes == null || lastMcvBytes.length == 0 || !metaReady || cachedFrameCount <= 0) {
            return;
        }
        EditPane editPane = getEditPane();
        if (editPane == null) {
            return;
        }
        TreePath path = editPane.getTree().getSelectionPath();
        if (path == null) {
            JMessageUtil.warn("操作提示", "请先在左侧树中选中当前视频节点。");
            return;
        }
        DefaultMutableTreeNode videoNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        WzObject selected = (WzObject) videoNode.getUserObject();
        if (!(selected instanceof WzVideoProperty video)) {
            JMessageUtil.warn("操作提示", "请选中 Canvas#Video 视频节点本身（不要选 UOL 等其它节点）。");
            return;
        }
        WzObject parentWz = video.getParent();
        if (!(parentWz instanceof WzImage) && !(parentWz instanceof WzImageProperty wip && wip.isListProperty())) {
            JMessageUtil.error("无法在父节点下添加图片：父节点类型不支持。");
            return;
        }
        WzImage wzImage = video.getWzImage();
        if (wzImage == null) {
            JMessageUtil.error("无法取得所属 img。");
            return;
        }
        if (!wzImage.parse()) {
            MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", wzImage.getName(), wzImage.getStatus().getMessage());
            JMessageUtil.error("img 解析失败，无法添加节点。");
            return;
        }
        int frameCount = (frames != null && !frames.isEmpty()) ? frames.size() : cachedFrameCount;
        for (int i = 0; i < frameCount; i++) {
            String nm = Integer.toString(i);
            if (parentWz instanceof WzImage wi && wi.existChild(nm)) {
                JMessageUtil.error("父节点下已存在名为 \"" + nm + "\" 的子节点，请先处理命名冲突。");
                return;
            }
            if (parentWz instanceof WzImageProperty wip && wip.isListProperty() && wip.existChild(nm)) {
                JMessageUtil.error("父节点下已存在名为 \"" + nm + "\" 的子节点，请先处理命名冲突。");
                return;
            }
        }
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) videoNode.getParent();
        if (parentNode == null) {
            JMessageUtil.error("树结构异常：视频节点无父节点。");
            return;
        }
        int videoDataIndex = indexOfChildProperty(parentWz, video);
        if (videoDataIndex < 0) {
            JMessageUtil.error("在父节点子列表中未找到当前视频，请尝试重新展开 img 后再试。");
            return;
        }
        int videoTreeIndex = parentNode.getIndex(videoNode);
        if (videoTreeIndex < 0) {
            JMessageUtil.error("在树中未找到当前视频节点位置。");
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(valuePane);
        VideoGenerateFrameNodesFormatDialog.Result formatChoice =
                VideoGenerateFrameNodesFormatDialog.open(owner != null ? owner : valuePane);
        if (formatChoice == null) {
            return;
        }
        final WzPngFormat pngFormat = formatChoice.format();
        final int pngScale = 0;

        btnGenerateFrameNodes.setEnabled(false);
        final WzObject parentRef = parentWz;
        final int videoIdx = videoDataIndex;
        final int treeIdx = videoTreeIndex;
        SwingWorker<List<WzCanvasProperty>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<WzCanvasProperty> doInBackground() throws Exception {
                List<BufferedImage> src = frames;
                boolean ownedDecode = false;
                if (src == null || src.isEmpty()) {
                    CanvasVideoFfmpegDecoder.DecodedVideo v = CanvasVideoFfmpegDecoder.decode(lastMcvBytes);
                    src = v.frames();
                    ownedDecode = true;
                }
                try {
                    if (src.size() < frameCount) {
                        throw new IllegalStateException("解码帧数与头信息不一致");
                    }
                    List<WzCanvasProperty> built = new ArrayList<>(frameCount);
                    for (int i = 0; i < frameCount; i++) {
                        BufferedImage raw = src.get(i);
                        BufferedImage argb = VideoArgbFormatConverter.apply(raw, VideoImageBitDepth.ARGB8888);
                        int w = argb.getWidth();
                        int h = argb.getHeight();
                        int ox = (int) Math.round(ORIGIN_REF_X * (double) w / ORIGIN_REF_WIDTH);
                        int oy = (int) Math.round(ORIGIN_REF_Y * (double) h / ORIGIN_REF_HEIGHT);
                        String frameName = Integer.toString(i);
                        WzCanvasProperty canvas = new WzCanvasProperty(frameName, parentRef, wzImage);
                        canvas.initPngProperty(frameName, canvas, wzImage);
                        canvas.setPng(argb, pngFormat, pngScale);
                        canvas.addChild(new WzIntProperty("delay", 60, canvas, wzImage));
                        canvas.addChild(new WzVectorProperty("origin", ox, oy, canvas, wzImage));
                        canvas.addChild(new WzIntProperty("z", 0, canvas, wzImage));
                        canvas.setTempChanged(true);
                        built.add(canvas);
                    }
                    return built;
                } finally {
                    if (ownedDecode) {
                        flushFrameList(src);
                    }
                }
            }

            @Override
            protected void done() {
                btnGenerateFrameNodes.setEnabled(true);
                try {
                    List<WzCanvasProperty> list = get();
                    for (int k = 0; k < list.size(); k++) {
                        WzCanvasProperty canvas = list.get(k);
                        int dataIndex = videoIdx + 1 + k;
                        boolean ok;
                        if (parentRef instanceof WzImage wi) {
                            ok = wi.addChildAt(canvas, dataIndex);
                        } else if (parentRef instanceof WzImageProperty wip) {
                            ok = wip.addChildAt(canvas, dataIndex);
                        } else {
                            ok = false;
                        }
                        if (!ok) {
                            JMessageUtil.error("添加节点 \"" + canvas.getName() + "\" 失败（可能重名）。");
                            return;
                        }
                        editPane.insertNodeToTree(parentNode, canvas, true, treeIdx + 1 + k);
                    }
                    JMessageUtil.info("已在视频同级添加 " + list.size() + " 个图片节点（名称 0～" + (list.size() - 1)
                            + "），格式 " + pngFormat.name() + "，含 delay/origin/z。");
                } catch (Exception ex) {
                    log.error("生成子节点失败", ex);
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JMessageUtil.error("生成子节点失败: " + c.getMessage());
                } finally {
                    releaseDecodedVideoMemory();
                }
            }
        };
        worker.execute();
    }

    private static int indexOfChildProperty(WzObject parent, WzVideoProperty video) {
        List<WzImageProperty> list = switch (parent) {
            case WzImage wi -> wi.getChildren();
            case WzImageProperty wip when wip.isListProperty() -> wip.getChildren();
            default -> null;
        };
        if (list == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == video) {
                return i;
            }
        }
        return -1;
    }

    private VideoImageSequenceExporter.ExportStats exportPngJpg(
            List<BufferedImage> frameList,
            Path dir,
            String prefix,
            String ext,
            List<Integer> sourceIndices,
            VideoImageBitDepth depth,
            BiConsumer<Integer, Integer> progressCallback
    ) throws IOException, InterruptedException {
        if (sourceIndices == null || sourceIndices.isEmpty()) {
            throw new IOException("没有选中任何帧");
        }
        String formatName = "jpg".equalsIgnoreCase(ext) ? "jpg" : "png";
        return VideoImageSequenceExporter.exportByFrameIndicesParallel(
                frameList, sourceIndices, dir, prefix, ext, formatName, depth, progressCallback);
    }

    private VideoImageSequenceExporter.ExportStats exportGifWithStats(
            List<BufferedImage> frameList,
            int[] delays,
            Path dir,
            String prefix,
            VideoImageBitDepth depth,
            int progressTotal,
            BiConsumer<Integer, Integer> progressCallback
    )
            throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(prefix + ".gif");
        int withAlpha = 0;
        List<Integer> failed = new ArrayList<>();
        List<BufferedImage> toEncode = new ArrayList<>(frameList.size());
        List<Integer> delayList = new ArrayList<>(frameList.size());
        int step = 0;
        for (int i = 0; i < frameList.size(); i++) {
            try {
                BufferedImage raw = frameList.get(i);
                if (VideoArgbFormatConverter.hasNonFullyOpaqueAlpha(raw)) {
                    withAlpha++;
                }
                toEncode.add(VideoArgbFormatConverter.apply(raw, depth));
                int d = delays[i];
                if (d < 1) {
                    d = 1;
                }
                if (d > 60_000) {
                    d = 60_000;
                }
                delayList.add(d);
            } catch (Exception e) {
                log.warn("GIF 预处理帧 {} 失败", i, e);
                failed.add(i);
            }
            step++;
            if (progressCallback != null) {
                progressCallback.accept(step, progressTotal);
            }
        }
        if (!failed.isEmpty()) {
            throw new IOException("GIF 预处理失败帧: " + failed.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        AnimatedGifEncoder enc = new AnimatedGifEncoder();
        enc.start(out.toAbsolutePath().toString());
        enc.setRepeat(0);
        enc.setQuality(5);
        try {
            for (int i = 0; i < toEncode.size(); i++) {
                enc.setDelay(delayList.get(i));
                enc.addFrame(toEncode.get(i));
                step++;
                if (progressCallback != null) {
                    progressCallback.accept(step, progressTotal);
                }
            }
            enc.finish();
            step++;
            if (progressCallback != null) {
                progressCallback.accept(step, progressTotal);
            }
        } catch (Exception e) {
            Files.deleteIfExists(out);
            throw new IOException("GIF 写入失败: " + e.getMessage(), e);
        }
        return new VideoImageSequenceExporter.ExportStats(frameList.size(), withAlpha, List.of());
    }

    private static String buildExportImageReport(VideoImageSequenceExporter.ExportStats st, Path dir) {
        StringBuilder sb = new StringBuilder();
        sb.append("导出目录:\n").append(dir.toAbsolutePath()).append("\n\n");
        sb.append("成功: ").append(st.successCount()).append(" 张\n");
        sb.append("添加了透明度（含半透明/透明像素）: ").append(st.withAlphaCount()).append(" 张\n");
        sb.append("失败: ").append(st.failedFrameIndices().size()).append(" 张\n");
        if (!st.failedFrameIndices().isEmpty()) {
            sb.append("失败帧序号: ");
            sb.append(st.failedFrameIndices().stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        return sb.toString();
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "video";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public void onHide() {
        super.onHide();
        stopPlayback();
        lastMcvBytes = null;
        metaReady = false;
    }

    @Override
    public VideoFormData getData() {
        return new VideoFormData(nameInput.getText(), typeInput.getText());
    }
}
