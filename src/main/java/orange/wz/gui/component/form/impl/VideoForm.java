package orange.wz.gui.component.form.impl;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.dialog.VideoExportImagesDialog;
import orange.wz.gui.component.form.data.VideoFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.component.panel.ImagePanel;
import orange.wz.gui.utils.JMessageUtil;
import orange.wz.gui.video.CanvasVideoFfmpegDecoder;
import orange.wz.gui.video.VideoArgbFormatConverter;
import orange.wz.gui.video.VideoImageBitDepth;
import orange.wz.gui.video.VideoImageSequenceExporter;
import orange.wz.gui.video.VideoPlaybackConstants;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzVideoProperty;

import javax.swing.*;
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

    private SwingWorker<CanvasVideoFfmpegDecoder.DecodedVideo, Void> loadWorker;
    private Timer animTimer;
    private List<BufferedImage> frames;
    private int[] delayMillis;
    private int frameIndex;
    private boolean playing;
    private byte[] lastMcvBytes;

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
        btnPlay.addActionListener(e -> togglePlay());
        btnExportVideo.addActionListener(e -> exportVideo());
        btnExportImages.addActionListener(e -> exportImages());
        btnRow.add(btnPlay);
        btnRow.add(btnExportVideo);
        btnRow.add(btnExportImages);

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
        stopPlayback();
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        frames = null;
        delayMillis = null;
        imagePanel.setImage(null);
        btnPlay.setEnabled(false);
        btnExportVideo.setEnabled(false);
        btnExportImages.setEnabled(false);
        lastMcvBytes = mcvBytes;

        statusLabel.setText("解码中…");
        loadWorker = new SwingWorker<>() {
            @Override
            protected CanvasVideoFfmpegDecoder.DecodedVideo doInBackground() throws Exception {
                return CanvasVideoFfmpegDecoder.decode(lastMcvBytes);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    CanvasVideoFfmpegDecoder.DecodedVideo v = get();
                    frames = v.frames();
                    delayMillis = v.delayMillis();
                    if (!frames.isEmpty()) {
                        imagePanel.setImage(frames.getFirst());
                        statusLabel.setText(frames.size() + " 帧 · " + frames.getFirst().getWidth() + "×" + frames.getFirst().getHeight());
                    } else {
                        statusLabel.setText("无帧");
                    }
                    btnPlay.setEnabled(!frames.isEmpty());
                    btnExportVideo.setEnabled(!frames.isEmpty());
                    btnExportImages.setEnabled(!frames.isEmpty());
                    imageScroll.revalidate();
                    imageScroll.repaint();
                } catch (InterruptedException | ExecutionException ex) {
                    log.error("视频解码失败", ex);
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("解码失败");
                    JMessageUtil.error("视频解码失败: " + c.getMessage());
                }
            }
        };
        loadWorker.execute();
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
        if (frames == null || frames.isEmpty()) {
            return;
        }
        if (playing) {
            stopPlayback();
        } else {
            playing = true;
            frameIndex = 0;
            imagePanel.setImage(frames.getFirst());
            btnPlay.setText("暂停");
            animTimer.stop();
            animTimer.setInitialDelay(VideoPlaybackConstants.FRAME_INTERVAL_MS);
            animTimer.setDelay(VideoPlaybackConstants.FRAME_INTERVAL_MS);
            animTimer.start();
        }
    }

    private void stopPlayback() {
        playing = false;
        animTimer.stop();
        btnPlay.setText("播放");
        if (frames != null && !frames.isEmpty()) {
            frameIndex = 0;
            imagePanel.setImage(frames.getFirst());
        }
    }

    private void exportVideo() {
        if (lastMcvBytes == null || frames == null || frames.isEmpty()) {
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
                CanvasVideoFfmpegDecoder.exportVideoFromFrames(frames, fout.toPath(), asMp4);
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
                }
            }
        };
        w.execute();
    }

    private void exportImages() {
        if (lastMcvBytes == null || frames == null || frames.isEmpty()) {
            return;
        }
        Window w = SwingUtilities.getWindowAncestor(valuePane);
        VideoExportImagesDialog dlg = new VideoExportImagesDialog(w, frames.size());
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
        final int gifProgressTotal = frames.size() * 2 + 1;
        final int barTotal = switch (fmt) {
            case PNG, JPG -> Math.max(1, indices.size());
            case GIF -> Math.max(1, gifProgressTotal);
            default -> 1;
        };
        BiConsumer<Integer, Integer> reportProgress = (done, total) -> SwingUtilities.invokeLater(() -> {
            MainFrame mf = MainFrame.getInstance();
            if (mf != null) {
                mf.updateProgress(done, total);
            }
        });

        SwingWorker<VideoImageSequenceExporter.ExportStats, Void> worker = new SwingWorker<>() {
            @Override
            protected VideoImageSequenceExporter.ExportStats doInBackground() throws Exception {
                SwingUtilities.invokeLater(() -> {
                    MainFrame mf = MainFrame.getInstance();
                    if (mf != null) {
                        mf.updateProgress(0, barTotal);
                    }
                });
                Files.createDirectories(dirFinal);
                return switch (fmt) {
                    case PNG -> exportPngJpg(dirFinal, prefix, "png", indices, depth, reportProgress);
                    case JPG -> exportPngJpg(dirFinal, prefix, "jpg", indices, depth, reportProgress);
                    case GIF -> exportGifWithStats(dirFinal, prefix, depth, gifProgressTotal, reportProgress);
                    default -> new VideoImageSequenceExporter.ExportStats(0, 0, List.of());
                };
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

    private VideoImageSequenceExporter.ExportStats exportPngJpg(
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
                frames, sourceIndices, dir, prefix, ext, formatName, depth, progressCallback);
    }

    private VideoImageSequenceExporter.ExportStats exportGifWithStats(
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
        List<BufferedImage> toEncode = new ArrayList<>(frames.size());
        List<Integer> delays = new ArrayList<>(frames.size());
        int step = 0;
        for (int i = 0; i < frames.size(); i++) {
            try {
                BufferedImage raw = frames.get(i);
                if (VideoArgbFormatConverter.hasNonFullyOpaqueAlpha(raw)) {
                    withAlpha++;
                }
                toEncode.add(VideoArgbFormatConverter.apply(raw, depth));
                int d = delayMillis[i];
                if (d < 1) {
                    d = 1;
                }
                if (d > 60_000) {
                    d = 60_000;
                }
                delays.add(d);
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
                enc.setDelay(delays.get(i));
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
        return new VideoImageSequenceExporter.ExportStats(frames.size(), withAlpha, List.of());
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
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
    }

    @Override
    public VideoFormData getData() {
        return new VideoFormData(nameInput.getText(), typeInput.getText());
    }
}
