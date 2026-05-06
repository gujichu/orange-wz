package orange.wz.gui.video;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 多线程按帧序号导出 PNG/JPG（参考 Outlink 固定线程池 + Future 等待）。
 */
@Slf4j
public final class VideoImageSequenceExporter {

    private static final Object IMAGE_IO_LOCK = new Object();

    public record ExportStats(int successCount, int withAlphaCount, List<Integer> failedFrameIndices) {
    }

    private VideoImageSequenceExporter() {
    }

    /**
     * @param allFrames     解码后的完整帧列表
     * @param sourceIndices 要导出的源帧下标（0 起始），顺序即输出文件序号 0000、0001…
     */
    public static ExportStats exportByFrameIndicesParallel(
            List<BufferedImage> allFrames,
            List<Integer> sourceIndices,
            Path dir,
            String filePrefix,
            String ext,
            String imageIoFormatName,
            VideoImageBitDepth depth,
            BiConsumer<Integer, Integer> progressCallback
    ) throws InterruptedException {
        if (sourceIndices == null || sourceIndices.isEmpty()) {
            return new ExportStats(0, 0, List.of());
        }
        final int totalTasks = sourceIndices.size();
        int cpu = Runtime.getRuntime().availableProcessors();
        int threadCount = Math.max(1, Math.min(20, (int) (cpu * 0.8)));
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger withAlpha = new AtomicInteger();
        AtomicInteger finishedTasks = new AtomicInteger();
        ConcurrentLinkedQueue<Integer> failed = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>(sourceIndices.size());
        try {
            for (int outSeq = 0; outSeq < sourceIndices.size(); outSeq++) {
                final int sequence = outSeq;
                final int srcIdx = sourceIndices.get(outSeq);
                futures.add(pool.submit(() -> {
                    try {
                        if (srcIdx < 0 || srcIdx >= allFrames.size()) {
                            failed.add(srcIdx);
                            return;
                        }
                        BufferedImage raw = allFrames.get(srcIdx);
                        BufferedImage processed = VideoArgbFormatConverter.apply(raw, depth);
                        BufferedImage out = ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext))
                                ? VideoArgbFormatConverter.flattenOnWhite(processed)
                                : processed;
                        Path f = dir.resolve(filePrefix + "_" + String.format("%04d", sequence) + "." + ext);
                        synchronized (IMAGE_IO_LOCK) {
                            ImageIO.write(out, imageIoFormatName, f.toFile());
                        }
                        success.incrementAndGet();
                        if (VideoArgbFormatConverter.hasNonFullyOpaqueAlpha(raw)) {
                            withAlpha.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.debug("写帧失败 src={}", srcIdx, e);
                        failed.add(srcIdx);
                    } finally {
                        int done = finishedTasks.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(done, totalTasks);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("导出任务异常", e);
                }
            }
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(30, TimeUnit.MINUTES)) {
                    log.warn("导出图片线程池未在 30 分钟内结束，尝试 shutdownNow");
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw e;
            }
        }
        List<Integer> failedList = new ArrayList<>(failed);
        Collections.sort(failedList);
        return new ExportStats(success.get(), withAlpha.get(), failedList);
    }
}
