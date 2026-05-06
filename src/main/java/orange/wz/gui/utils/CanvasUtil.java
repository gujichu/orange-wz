package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzPngProperty;
import orange.wz.provider.properties.WzPngZlibCompressMode;
import orange.wz.provider.tools.ImgTool;
import orange.wz.provider.tools.SmartImageResizeTool;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class CanvasUtil {
    public static void search(List<CanvasUtilData> result, List<? extends WzObject> wzObjects) {
        for (WzObject wzObject : wzObjects) {
            if (wzObject instanceof WzCanvasProperty canvas) {
                result.add(new CanvasUtilData(
                        canvas.getPath(),
                        canvas.getPngImage(false),
                        canvas.getWidth(),
                        canvas.getHeight(),
                        canvas.getFormat()
                ));
            } else if (wzObject instanceof WzImageProperty prop && prop.isListProperty()) {
                search(result, prop.getChildren());
            } else if (wzObject instanceof WzDirectory wzDir) {
                if (wzDir.isWzFile() && !wzDir.getWzFile().parse()) {
                    MainFrame.getInstance().setStatusText("文件 %s 解析失败", wzDir.getName());
                    throw new RuntimeException();
                }
                search(result, wzDir.getChildren());
            } else if (wzObject instanceof WzImage wzImg) {
                if (!wzImg.parse()) {
                    MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", wzImg.getName(), wzImg.getStatus().getMessage());
                    throw new RuntimeException();
                }
                search(result, wzImg.getChildren());
            }
        }
    }

    public static void changeFormat(List<WzImageProperty> properties, WzPngFormat format) {
        for (WzImageProperty prop : properties) {
            if (prop instanceof WzCanvasProperty canvas) {
                if (canvas.getFormat() == format) continue;
                canvas.setPng(canvas.getPngImage(false), format, 0);
                MainFrame.getInstance().setStatusText("已处理 %s", canvas.getPath());
            } else if (prop.isListProperty()) {
                changeFormat(prop.getChildren(), format);
            }
        }
    }

    public static void scaleImage(List<WzImageProperty> properties, double scale) {
        for (WzImageProperty prop : properties) {
            if (prop instanceof WzCanvasProperty canvas) {
                canvas.scale(scale);
                MainFrame.getInstance().setStatusText("已处理 %s", canvas.getPath());
            } else if (prop.isListProperty()) {
                scaleImage(prop.getChildren(), scale);
            }
        }
    }

    /**
     * 多线程批量修改图片格式（高效优化版本）
     *
     * @param root             根节点
     * @param targetFormat     目标格式
     * @param excludedNames    要排除的节点名称（可选）
     * @param parentComponent  用于显示对话框的父组件
     * @param onComplete       完成后的回调（可选）
     */
    public static void changeFormatMultiThread(WzObject root, WzPngFormat targetFormat,
                                               List<String> excludedNames, JComponent parentComponent,
                                               Runnable onComplete) {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // 步骤1：递归收集所有需要处理的 Canvas 节点（只读阶段）
                List<WzCanvasProperty> allCanvases = new ArrayList<>();
                collectCanvases(root, allCanvases, excludedNames, targetFormat);

                if (allCanvases.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent, "没有需要处理的图片", "提示", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                    return null;
                }

                // 步骤2：计算动态线程数（CPU核心数的80%，最少1，最多20）
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                int threadCount = Math.max(1, Math.min(20, (int) (availableProcessors * 0.8)));
                log.info("使用 {} 个线程处理 {} 个图片", threadCount, allCanvases.size());

                // 步骤3：创建线程池
                ThreadPoolExecutor executor = new ThreadPoolExecutor(
                        threadCount, threadCount,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(),
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("CanvasFormat-" + t.getId());
                            t.setDaemon(true);
                            return t;
                        }
                );

                // 步骤4：使用同步列表收集失败信息
                List<String> failedPaths = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger skippedCount = new AtomicInteger(0);

                // 步骤5：提交任务（只写内存，避免竞争）
                List<Future<?>> futures = new ArrayList<>();
                for (WzCanvasProperty canvas : allCanvases) {
                    Future<?> future = executor.submit(() -> {
                        try {
                            // 尝试获取图片数据，先尝试保存到内存
                            var imageData = canvas.getPngImage(true);

                            if (imageData == null) {
                                // 再试一次不保存到内存
                                imageData = canvas.getPngImage(false);
                                if (imageData == null) {
                                    throw new RuntimeException("图片数据为 null");
                                }
                            }

                            // 只写内存修改格式
                            canvas.setPng(imageData, targetFormat, 0);

                            int current = successCount.incrementAndGet();
                            if (current % 10 == 0 || current == allCanvases.size()) {
                                MainFrame.getInstance().setStatusText("处理进度: %d/%d", current, allCanvases.size());
                            }

                        } catch (Throwable e) {  // 捕获 Throwable 以确保不遗漏任何问题
                            // 检查是否是常见的 ZLIB 或 EOF 错误，如果是，跳过
                            boolean isKnownError = false;
                            String message = e.getMessage();
                            Throwable cause = e.getCause();
                            if (message != null) {
                                if (message.contains("Unexpected end of ZLIB input stream") ||
                                    message.contains("EOFException") ||
                                    message.contains("BufferOverflow") ||
                                    message.contains("BufferUnderflow")) {
                                    isKnownError = true;
                                }
                            }
                            if (cause != null) {
                                String causeMsg = cause.getMessage();
                                if (causeMsg != null && (
                                    causeMsg.contains("Unexpected end of ZLIB input stream") ||
                                    causeMsg.contains("EOFException"))) {
                                    isKnownError = true;
                                }
                            }

                            if (isKnownError) {
                                skippedCount.incrementAndGet();
                                log.warn("跳过无法解析的图片: {}", canvas.getPath());
                            } else {
                                log.error("处理图片失败: {}", canvas.getPath(), e);
                                String errorMsg;
                                if (e.getMessage() != null) {
                                    errorMsg = e.getMessage();
                                } else {
                                    errorMsg = e.getClass().getSimpleName();
                                }
                                // 获取完整的异常链
                                if (cause != null && cause.getMessage() != null) {
                                    errorMsg += " (Cause: " + cause.getMessage() + ")";
                                }
                                failedPaths.add(canvas.getPath() + " - " + errorMsg);
                            }
                        }
                    });
                    futures.add(future);
                }

                // 步骤6：等待所有任务完成
                try {
                    for (Future<?> future : futures) {
                        future.get();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("处理被中断", e);
                } catch (ExecutionException e) {
                    log.error("执行异常", e);
                } finally {
                    // 步骤7：关闭线程池并回收资源
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }

                    // 帮助GC
                    allCanvases.clear();
                    futures.clear();
                }

                // 步骤8：显示结果
                int success = successCount.get();
                int skipped = skippedCount.get();
                int failed = failedPaths.size();

                MainFrame.getInstance().setStatusText("处理完成: 成功 %d, 跳过 %d, 失败 %d", success, skipped, failed);

                SwingUtilities.invokeLater(() -> {
                    if (failed > 0 || skipped > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("处理完成:\n");
                        sb.append("成功: ").append(success).append(" 张, 跳过: ").append(skipped).append(" 张, 失败: ").append(failed).append(" 张\n\n");

                        if (failed > 0) {
                            sb.append("失败列表:\n");
                            // 限制显示的失败数量，避免对话框过大
                            int maxShow = Math.min(failed, 50);
                            for (int i = 0; i < maxShow; i++) {
                                sb.append(failedPaths.get(i)).append("\n");
                            }
                            if (failed > maxShow) {
                                sb.append("\n... 还有 ").append(failed - maxShow).append(" 个错误未显示");
                            }
                        }

                        JOptionPane.showMessageDialog(parentComponent, sb.toString(), "处理完成", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parentComponent, "所有图片处理成功！共处理 " + success + " 张", "成功", JOptionPane.INFORMATION_MESSAGE);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                return null;
            }
        };
        worker.execute();
    }

    /**
     * 图片压缩：仅压缩高于 ARGB8888 的格式（即 value > 2）
     *
     * @param root             根节点
     * @param excludedNames    要排除的节点名称（可选）
     * @param parentComponent  用于显示对话框的父组件
     * @param onComplete       完成后的回调（可选）
     */
    public static void compressImages(WzObject root, List<String> excludedNames,
                                      JComponent parentComponent, Runnable onComplete) {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // 步骤1：递归收集所有需要处理的 Canvas 节点（只读阶段，仅收集高于 ARGB8888 的）
                List<WzCanvasProperty> allCanvases = new ArrayList<>();
                collectCanvasesForCompress(root, allCanvases, excludedNames);

                if (allCanvases.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent, "没有需要压缩的图片（已是 ARGB8888 或更低格式）", "提示", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                    return null;
                }

                // 步骤2：计算动态线程数（CPU核心数的80%，最少1，最多20）
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                int threadCount = Math.max(1, Math.min(20, (int) (availableProcessors * 0.8)));
                log.info("使用 {} 个线程压缩 {} 个图片", threadCount, allCanvases.size());

                // 步骤3：创建线程池
                ThreadPoolExecutor executor = new ThreadPoolExecutor(
                        threadCount, threadCount,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(),
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("CanvasCompress-" + t.getId());
                            t.setDaemon(true);
                            return t;
                        }
                );

                // 步骤4：使用同步列表收集失败信息
                List<String> failedPaths = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger skippedCount = new AtomicInteger(0);

                // 步骤5：提交任务（只写内存，避免竞争）
                List<Future<?>> futures = new ArrayList<>();
                for (WzCanvasProperty canvas : allCanvases) {
                    Future<?> future = executor.submit(() -> {
                        try {
                            // 尝试获取图片数据，先尝试保存到内存
                            var imageData = canvas.getPngImage(true);

                            if (imageData == null) {
                                // 再试一次不保存到内存
                                imageData = canvas.getPngImage(false);
                                if (imageData == null) {
                                    throw new RuntimeException("图片数据为 null");
                                }
                            }

                            // 只写内存修改格式为 ARGB8888
                            canvas.setPng(imageData, WzPngFormat.ARGB8888, 0);

                            int current = successCount.incrementAndGet();
                            if (current % 10 == 0 || current == allCanvases.size()) {
                                MainFrame.getInstance().setStatusText("压缩进度: %d/%d", current, allCanvases.size());
                            }

                        } catch (Throwable e) {  // 捕获 Throwable 以确保不遗漏任何问题
                            // 检查是否是常见的 ZLIB 或 EOF 错误，如果是，跳过
                            boolean isKnownError = false;
                            String message = e.getMessage();
                            Throwable cause = e.getCause();
                            if (message != null) {
                                if (message.contains("Unexpected end of ZLIB input stream") ||
                                    message.contains("EOFException") ||
                                    message.contains("BufferOverflow") ||
                                    message.contains("BufferUnderflow")) {
                                    isKnownError = true;
                                }
                            }
                            if (cause != null) {
                                String causeMsg = cause.getMessage();
                                if (causeMsg != null && (
                                    causeMsg.contains("Unexpected end of ZLIB input stream") ||
                                    causeMsg.contains("EOFException"))) {
                                    isKnownError = true;
                                }
                            }

                            if (isKnownError) {
                                skippedCount.incrementAndGet();
                                log.warn("跳过无法解析的图片: {}", canvas.getPath());
                            } else {
                                log.error("压缩图片失败: {}", canvas.getPath(), e);
                                String errorMsg;
                                if (e.getMessage() != null) {
                                    errorMsg = e.getMessage();
                                } else {
                                    errorMsg = e.getClass().getSimpleName();
                                }
                                // 获取完整的异常链
                                if (cause != null && cause.getMessage() != null) {
                                    errorMsg += " (Cause: " + cause.getMessage() + ")";
                                }
                                failedPaths.add(canvas.getPath() + " - " + errorMsg);
                            }
                        }
                    });
                    futures.add(future);
                }

                // 步骤6：等待所有任务完成
                try {
                    for (Future<?> future : futures) {
                        future.get();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("压缩被中断", e);
                } catch (ExecutionException e) {
                    log.error("执行异常", e);
                } finally {
                    // 步骤7：关闭线程池并回收资源
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }

                    // 帮助GC
                    allCanvases.clear();
                    futures.clear();
                }

                // 步骤8：显示结果
                int success = successCount.get();
                int skipped = skippedCount.get();
                int failed = failedPaths.size();

                MainFrame.getInstance().setStatusText("压缩完成: 成功 %d, 跳过 %d, 失败 %d", success, skipped, failed);

                SwingUtilities.invokeLater(() -> {
                    if (failed > 0 || skipped > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("压缩完成:\n");
                        sb.append("成功: ").append(success).append(" 张, 跳过: ").append(skipped).append(" 张, 失败: ").append(failed).append(" 张\n\n");

                        if (failed > 0) {
                            sb.append("失败列表:\n");
                            // 限制显示的失败数量，避免对话框过大
                            int maxShow = Math.min(failed, 50);
                            for (int i = 0; i < maxShow; i++) {
                                sb.append(failedPaths.get(i)).append("\n");
                            }
                            if (failed > maxShow) {
                                sb.append("\n... 还有 ").append(failed - maxShow).append(" 个错误未显示");
                            }
                        }

                        JOptionPane.showMessageDialog(parentComponent, sb.toString(), "压缩完成", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parentComponent, "所有图片压缩成功！共处理 " + success + " 张", "成功", JOptionPane.INFORMATION_MESSAGE);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                return null;
            }
        };
        worker.execute();
    }

    private static final int STRONG_COMPRESS_BATCH = 280;

    private record StrongCompressPending(WzCanvasProperty canvas,
                                         WzPngProperty.CompressedPngData backup,
                                         BufferedImage workImage,
                                         long beforeSize,
                                         WzPngFormat sourceFormat,
                                         int sourceScale) {
    }

    /**
     * 强力压缩：分批单线程读取解码 + 多线程写回（与 Outlink 写入模式类似，写时 synchronized(canvas)）。
     */
    public static void strongCompressImages(WzObject root, StrongCompressOptions options,
                                            List<String> excludedNames, JComponent parentComponent,
                                            Runnable onComplete) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                List<WzCanvasProperty> all = new ArrayList<>();
                collectAllCanvases(root, all, excludedNames);
                if (all.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent, "当前节点下没有可处理的图片", "提示", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                    return null;
                }

                final int totalCollected = all.size();

                int threadCount = Math.max(1, Math.min(20, (int) (Runtime.getRuntime().availableProcessors() * 0.8)));
                log.info("强力压缩开始 节点下图片数={} 线程={} 格式={} ZLIB={} zlib算法={} 像素量化={} LIQ(色数={},speed={},抖动={}) pngScale={} FS抖动={} 体积未减小则还原={}",
                        all.size(), threadCount, options.targetFormat(), options.zlibLevel(), options.zlibMode(),
                        options.quantMode(),
                        options.liqMaxColors(), options.liqSpeed(), options.liqDitherLevel(),
                        options.pngScale(), options.floydSteinbergDither(), options.skipIfNotSmaller());

                ExecutorService writeExecutor = Executors.newFixedThreadPool(threadCount, r -> {
                    Thread t = new Thread(r);
                    t.setName("StrongCompress-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });

                AtomicInteger successApply = new AtomicInteger(0);
                AtomicInteger reverted = new AtomicInteger(0);
                AtomicInteger skippedRead = new AtomicInteger(0);
                AtomicInteger failed = new AtomicInteger(0);
                AtomicLong sumBefore = new AtomicLong(0);
                AtomicLong sumAfter = new AtomicLong(0);

                try {
                    WzPngFormat targetFormat = options.targetFormat();
                    int pngScale = (targetFormat == WzPngFormat.ARGB4444 || targetFormat == WzPngFormat.RGB565)
                            ? options.pngScale() : 0;
                    int zlibLevel = options.zlibLevel();
                    WzPngZlibCompressMode zlibMode = options.zlibMode();

                    for (int start = 0, batchNo = 1; start < all.size(); start += STRONG_COMPRESS_BATCH, batchNo++) {
                        int end = Math.min(start + STRONG_COMPRESS_BATCH, all.size());
                        List<WzCanvasProperty> batch = all.subList(start, end);
                        MainFrame.getInstance().setStatusTextDirect(
                                String.format("强力压缩 读取第 %d 批 (%d 张)...", batchNo, batch.size()));

                        List<StrongCompressPending> pending = new ArrayList<>(batch.size());
                        for (WzCanvasProperty canvas : batch) {
                            String path = canvas.getPath();
                            try {
                                WzPngProperty.CompressedPngData backup = canvas.exportCompressedPngData();
                                if (backup == null || backup.getCompressedBytes() == null) {
                                    failed.incrementAndGet();
                                    String msg = "导出压缩数据为空";
                                    log.warn("强力压缩 FAIL {} | {}", path, msg);
                                    continue;
                                }
                                long beforeLen = backup.getCompressedBytes().length;
                                int block = pngScale > 0 ? (1 << pngScale) : 1;
                                if (pngScale > 0 && (canvas.getWidth() % block != 0 || canvas.getHeight() % block != 0)) {
                                    failed.incrementAndGet();
                                    String msg = String.format("尺寸 %dx%d 不能被块缩放 %d 整除",
                                            canvas.getWidth(), canvas.getHeight(), block);
                                    log.warn("强力压缩 FAIL {} | {}", path, msg);
                                    continue;
                                }

                                BufferedImage src = canvas.getPngImage(false);
                                if (src == null) {
                                    src = canvas.getPngImage(true);
                                }
                                if (src == null) {
                                    failed.incrementAndGet();
                                    log.warn("强力压缩 FAIL {} | 解码为 null", path);
                                    continue;
                                }
                                BufferedImage work = copyImageToArgb(src);
                                src.flush();
                                pending.add(new StrongCompressPending(
                                        canvas,
                                        backup,
                                        work,
                                        beforeLen,
                                        canvas.getFormat(),
                                        canvas.getScale()));
                            } catch (Throwable e) {
                                if (isKnownCorruptImageError(e)) {
                                    skippedRead.incrementAndGet();
                                    log.warn("强力压缩 SKIP_READ {} | {}", path, shortError(e));
                                } else {
                                    failed.incrementAndGet();
                                    log.error("强力压缩 FAIL {} | {}", path, shortError(e), e);
                                }
                            }
                        }

                        if (pending.isEmpty()) {
                            continue;
                        }

                        List<Future<?>> futures = new ArrayList<>(pending.size());
                        for (StrongCompressPending p : pending) {
                            futures.add(writeExecutor.submit(() -> {
                                BufferedImage img = p.workImage();
                                try {
                                    if (options.quantMode() == StrongCompressQuantMode.LIBIMAGEQUANT) {
                                        BufferedImage liqOut = LibimagequantStrongCompress.quantizeToArgb8888(
                                                img,
                                                options.liqMaxColors(),
                                                options.liqSpeed(),
                                                options.liqDitherLevel());
                                        img.flush();
                                        img = liqOut;
                                    } else if (options.floydSteinbergDither()) {
                                        if (targetFormat == WzPngFormat.ARGB4444) {
                                            ImgTool.Argb32.floydSteinbergArgb4444(img);
                                        } else if (targetFormat == WzPngFormat.RGB565) {
                                            ImgTool.Argb32.floydSteinbergRgb565(img);
                                        }
                                    }
                                    synchronized (p.canvas()) {
                                        p.canvas().setPng(img, targetFormat, pngScale, zlibLevel, zlibMode);
                                        int afterLen = p.canvas().getCompressedPngStorageLength();
                                        // DXT5/BC7 等块压缩往往比 ARGB8888+zlib 更小：勾选「未变小则还原」时不应冲掉显式格式转换
                                        boolean packagingChanged = p.sourceFormat() != targetFormat || p.sourceScale() != pngScale;
                                        if (options.skipIfNotSmaller() && afterLen >= p.beforeSize() && !packagingChanged) {
                                            p.canvas().copyPngFromCompressedData(p.backup(), false);
                                            afterLen = p.canvas().getCompressedPngStorageLength();
                                            reverted.incrementAndGet();
                                        } else {
                                            if (options.skipIfNotSmaller() && afterLen >= p.beforeSize() && packagingChanged) {
                                                log.info("强力压缩 体积未减小但已切换封装 {}→{} scale {}→{}，保留新格式",
                                                        p.sourceFormat(), targetFormat, p.sourceScale(), pngScale);
                                            }
                                            successApply.incrementAndGet();
                                        }
                                        p.canvas().clearImage();
                                        sumBefore.addAndGet(p.beforeSize());
                                        sumAfter.addAndGet(afterLen);
                                    }
                                } catch (Throwable e) {
                                    failed.incrementAndGet();
                                    try {
                                        synchronized (p.canvas()) {
                                            p.canvas().copyPngFromCompressedData(p.backup(), false);
                                            p.canvas().clearImage();
                                        }
                                    } catch (Exception restoreEx) {
                                        log.error("强力压缩回滚失败: {}", p.canvas().getPath(), restoreEx);
                                    }
                                    log.error("强力压缩 FAIL {} | {}", p.canvas().getPath(), shortError(e), e);
                                } finally {
                                    if (img != null) {
                                        img.flush();
                                    }
                                }
                            }));
                        }

                        for (Future<?> f : futures) {
                            try {
                                f.get();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                                log.error("强力压缩任务异常", e);
                            }
                        }
                        pending.clear();
                        System.gc();
                    }
                } catch (Exception e) {
                    log.error("强力压缩主流程异常", e);
                } finally {
                    writeExecutor.shutdown();
                    try {
                        if (!writeExecutor.awaitTermination(30, TimeUnit.MINUTES)) {
                            writeExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        writeExecutor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    all.clear();
                }

                long sb = sumBefore.get();
                long sa = sumAfter.get();
                double ratioPct = sb > 0 ? (100.0 * (sb - sa) / sb) : 0.0;

                int ok = successApply.get();
                int rev = reverted.get();
                int sk = skippedRead.get();
                int fl = failed.get();

                log.info("强力压缩结束 成功应用={} 还原={} 读取跳过={} 失败={} 压缩前={}B 压缩后={}B 体积变化率={}%",
                        ok, rev, sk, fl, sb, sa, String.format("%.2f", ratioPct));
                MainFrame.getInstance().setStatusText(
                        "强力压缩完成 成功:%d 还原:%d 跳过:%d 失败:%d", ok, rev, sk, fl);

                SwingUtilities.invokeLater(() -> {
                    StringBuilder msg = new StringBuilder();
                    msg.append(String.format("处理张数（收集）: %d\n", totalCollected));
                    msg.append(String.format("成功应用新压缩: %d\n", ok));
                    msg.append(String.format("已还原（体积未减小）: %d\n", rev));
                    msg.append(String.format("读取阶段跳过（损坏）: %d\n", sk));
                    msg.append(String.format("失败: %d\n\n", fl));
                    msg.append(String.format("压缩前总大小: %,d 字节\n", sb));
                    msg.append(String.format("压缩后总大小: %,d 字节\n", sa));
                    msg.append(String.format("体积变化率: %.2f%%（正数表示变小）\n", ratioPct));
                    msg.append("\n详细过程见控制台日志（强力压缩 / 强力压缩 FAIL 等前缀）。");

                    int type = fl > 0 || sk > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
                    JOptionPane.showMessageDialog(parentComponent, msg.toString(), "强力压缩完成", type);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                return null;
            }
        };
        worker.execute();
    }

    private static BufferedImage copyImageToArgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, source.getWidth(), source.getHeight(),
                source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth()),
                0, source.getWidth());
        return copy;
    }

    private static boolean isKnownCorruptImageError(Throwable e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (message != null) {
            if (message.contains("Unexpected end of ZLIB input stream")
                    || message.contains("EOFException")
                    || message.contains("BufferOverflow")
                    || message.contains("BufferUnderflow")) {
                return true;
            }
        }
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && (causeMsg.contains("Unexpected end of ZLIB input stream")
                    || causeMsg.contains("EOFException"))) {
                return true;
            }
        }
        return false;
    }

    private static String shortError(Throwable e) {
        String m = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable c = e.getCause();
        if (c != null && c.getMessage() != null) {
            m += " (Cause: " + c.getMessage() + ")";
        }
        return m;
    }

    private static void collectAllCanvases(WzObject node, List<WzCanvasProperty> result, List<String> excludedNames) {
        if (node instanceof WzCanvasProperty canvas) {
            boolean excluded = false;
            if (excludedNames != null && !excludedNames.isEmpty()) {
                for (String name : excludedNames) {
                    if (canvas.getName().equals(name)) {
                        excluded = true;
                        break;
                    }
                }
            }
            if (!excluded) {
                result.add(canvas);
            }
        } else if (node instanceof WzImageProperty prop && prop.isListProperty()) {
            List<? extends WzObject> children = prop.getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectAllCanvases(child, result, excludedNames);
                }
            }
        } else if (node instanceof WzImage) {
            List<? extends WzObject> children = ((WzImage) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectAllCanvases(child, result, excludedNames);
                }
            }
        } else if (node instanceof WzDirectory) {
            List<? extends WzObject> children = ((WzDirectory) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectAllCanvases(child, result, excludedNames);
                }
            }
        }
    }

    /**
     * 多线程批量修改图片格式（高效优化版本）
     *
     * @param root             根节点
     * @param targetFormat     目标格式
     * @param excludedNames    要排除的节点名称（可选）
     * @param parentComponent  用于显示对话框的父组件
     */
    public static void changeFormatMultiThread(WzObject root, WzPngFormat targetFormat,
                                               List<String> excludedNames, JComponent parentComponent) {
        changeFormatMultiThread(root, targetFormat, excludedNames, parentComponent, null);
    }

    /**
     * 多线程批量调整图片尺寸（先按目标宽度等比缩放，再上下居中裁剪）
     *
     * @param canvases         要处理的 Canvas 列表
     * @param targetWidth      目标宽度
     * @param targetHeight     目标高度
     * @param parentComponent  用于显示对话框的父组件
     * @param onComplete       完成后的回调（可选）
     */
    public static void resizeImageSizeMultiThread(List<WzCanvasProperty> canvases, int targetWidth, int targetHeight,
                                                  JComponent parentComponent, Runnable onComplete) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private static final int TASKS_PER_BATCH = 300;

            record ResizeTask(WzCanvasProperty canvas, String path, BufferedImage sourceImage) {
            }

            @Override
            protected Void doInBackground() {
                if (canvases == null || canvases.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent, "没有需要处理的图片", "提示", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                    return null;
                }

                List<WzCanvasProperty> taskCanvases = new ArrayList<>(canvases.size());
                int skippedBeforeSubmit = 0;
                for (WzCanvasProperty canvas : canvases) {
                    if (canvas == null || (canvas.getWidth() == targetWidth && canvas.getHeight() == targetHeight)) {
                        skippedBeforeSubmit++;
                        continue;
                    }
                    taskCanvases.add(canvas);
                }

                int total = canvases.size();
                if (taskCanvases.isEmpty()) {
                    MainFrame.getInstance().updateProgress(total, total);
                    MainFrame.getInstance().setStatusText("图片大小调整完成: 总计 %d, 跳过 %d, 完成 %d", total, skippedBeforeSubmit, 0);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent, "没有需要调整的图片（尺寸均已匹配）", "提示", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                    return null;
                }

                int cpuCores = Runtime.getRuntime().availableProcessors();
                int threadCount = Math.max(1, Math.min(20, (int) (cpuCores * 0.8)));
                final int skippedFinal = skippedBeforeSubmit;
                log.info("图片尺寸调整使用 {} 个线程处理 {} 张图片（总计 {}，预跳过 {}）", threadCount, taskCanvases.size(), total, skippedFinal);

                ExecutorService writeExecutor = Executors.newFixedThreadPool(threadCount, r -> {
                    Thread t = new Thread(r);
                    t.setName("CanvasResize-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });

                ConcurrentLinkedQueue<String> failedPaths = new ConcurrentLinkedQueue<>();
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                AtomicInteger finishedCount = new AtomicInteger(skippedFinal);

                MainFrame.getInstance().updateProgress(skippedFinal, total);
                MainFrame.getInstance().setStatusText("开始调整图片大小: 总计 %d，预跳过 %d，并行处理 %d", total, skippedFinal, taskCanvases.size());

                try {
                    int batchNo = 1;
                    for (int start = 0; start < taskCanvases.size(); start += TASKS_PER_BATCH, batchNo++) {
                        int end = Math.min(start + TASKS_PER_BATCH, taskCanvases.size());
                        List<WzCanvasProperty> batch = taskCanvases.subList(start, end);

                        MainFrame.getInstance().setStatusTextDirect(
                                String.format("正在读取第 %d 批资源 (%d 个任务)...", batchNo, batch.size())
                        );

                        // 阶段1：单线程读取（避免解析竞争）
                        List<ResizeTask> readyTasks = new ArrayList<>(batch.size());
                        for (WzCanvasProperty canvas : batch) {
                            String path = canvas.getPath();
                            BufferedImage source = null;
                            try {
                                source = canvas.getPngImage(false);
                                if (source == null) {
                                    throw new RuntimeException("图片数据为 null");
                                }
                                readyTasks.add(new ResizeTask(canvas, path, copyToArgb(source)));
                            } catch (Throwable e) {
                                failedCount.incrementAndGet();
                                failedPaths.add(path + " - 读取阶段失败: " + buildErrorMessage(e));
                                log.error("图片大小读取失败: {}", path, e);
                                MainFrame.getInstance().updateProgress(finishedCount.incrementAndGet(), total);
                            } finally {
                                if (source != null) {
                                    source.flush();
                                }
                            }
                        }

                        // 阶段2：多线程写入（仅内存写）
                        List<Future<?>> futures = new ArrayList<>(readyTasks.size());
                        for (ResizeTask task : readyTasks) {
                            futures.add(writeExecutor.submit(() -> {
                                BufferedImage resized = null;
                                try {
                                    resized = SmartImageResizeTool.resizeByWidthAndCenterCrop(task.sourceImage(), targetWidth, targetHeight);
                                    WzCanvasProperty canvas = task.canvas();
                                    synchronized (canvas) {
                                        canvas.setPng(resized, canvas.getFormat(), canvas.getScale());
                                        canvas.clearImage();
                                    }
                                    successCount.incrementAndGet();
                                } catch (Throwable e) {
                                    failedCount.incrementAndGet();
                                    failedPaths.add(task.path() + " - 写入阶段失败: " + buildErrorMessage(e));
                                    log.error("图片大小写入失败: {}", task.path(), e);
                                } finally {
                                    if (task.sourceImage() != null) {
                                        task.sourceImage().flush();
                                    }
                                    if (resized != null) {
                                        resized.flush();
                                    }

                                    int current = finishedCount.incrementAndGet();
                                    if (current % 10 == 0 || current == total) {
                                        MainFrame.getInstance().updateProgress(current, total);
                                        MainFrame.getInstance().setStatusText(
                                                "图片大小处理中: %d/%d (成功 %d, 失败 %d, 跳过 %d)",
                                                current, total, successCount.get(), failedCount.get(), skippedFinal
                                        );
                                    }
                                }
                            }));
                        }

                        for (Future<?> future : futures) {
                            try {
                                future.get();
                            } catch (Exception e) {
                                failedCount.incrementAndGet();
                                failedPaths.add("UNKNOWN - 线程任务异常: " + buildErrorMessage(e));
                            }
                        }

                        futures.clear();
                        readyTasks.clear();
                        System.gc();
                    }
                } finally {
                    writeExecutor.shutdown();
                    try {
                        if (!writeExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                            writeExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        writeExecutor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    taskCanvases.clear();
                }

                int success = successCount.get();
                int failed = failedCount.get();
                MainFrame.getInstance().updateProgress(total, total);
                MainFrame.getInstance().setStatusText("图片大小调整完成: 总计 %d, 跳过 %d, 完成 %d, 失败 %d", total, skippedFinal, success, failed);

                SwingUtilities.invokeLater(() -> {
                    if (failed > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("图片大小调整完成:\n");
                        sb.append("总计: ").append(total)
                                .append(", 完成: ").append(success)
                                .append(", 跳过: ").append(skippedFinal)
                                .append(", 失败: ").append(failed)
                                .append("\n\n失败路径:\n");
                        for (String failedPath : failedPaths) {
                            sb.append(failedPath).append("\n");
                        }
                        JOptionPane.showMessageDialog(parentComponent, sb.toString(), "处理完成（含失败）", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(
                                parentComponent,
                                String.format("图片大小调整完成！总计 %d，完成 %d，跳过 %d。", total, success, skippedFinal),
                                "成功",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

                failedPaths.clear();
                return null;
            }

            private BufferedImage copyToArgb(BufferedImage source) {
                BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                copy.setRGB(0, 0, source.getWidth(), source.getHeight(),
                        source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth()),
                        0, source.getWidth());
                return copy;
            }

            private String buildErrorMessage(Throwable e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage() != null) {
                    message += " (Cause: " + cause.getMessage() + ")";
                }
                return message;
            }
        };
        worker.execute();
    }

    /**
     * 递归收集所有需要处理的 Canvas 节点（用于图片压缩）
     * 仅处理格式高于 ARGB8888 的图片（即 value > 2）
     */
    private static void collectCanvasesForCompress(WzObject node, List<WzCanvasProperty> result,
                                                   List<String> excludedNames) {
        if (node instanceof WzCanvasProperty canvas) {
            // 检查是否在排除列表中
            boolean excluded = false;
            if (excludedNames != null && !excludedNames.isEmpty()) {
                for (String name : excludedNames) {
                    if (canvas.getName().equals(name)) {
                        excluded = true;
                        break;
                    }
                }
            }
            // 如果没有被排除，且格式高于 ARGB8888（即 value > 2），则添加到列表
            if (!excluded && canvas.getFormat().getValue() > 2) {
                result.add(canvas);
            }
        } else if (node instanceof WzImageProperty prop && prop.isListProperty()) {
            List<? extends WzObject> children = prop.getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvasesForCompress(child, result, excludedNames);
                }
            }
        } else if (node instanceof WzImage) {
            List<? extends WzObject> children = ((WzImage) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvasesForCompress(child, result, excludedNames);
                }
            }
        } else if (node instanceof WzDirectory) {
            List<? extends WzObject> children = ((WzDirectory) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvasesForCompress(child, result, excludedNames);
                }
            }
        }
    }

    /**
     * 递归收集所有需要处理的 Canvas 节点（用于批量格式修改）
     */
    private static void collectCanvases(WzObject node, List<WzCanvasProperty> result,
                                        List<String> excludedNames, WzPngFormat targetFormat) {
        if (node instanceof WzCanvasProperty canvas) {
            // 检查是否在排除列表中
            boolean excluded = false;
            if (excludedNames != null && !excludedNames.isEmpty()) {
                for (String name : excludedNames) {
                    if (canvas.getName().equals(name)) {
                        excluded = true;
                        break;
                    }
                }
            }
            // 如果没有被排除，且格式与目标不同，则添加到列表
            if (!excluded && canvas.getFormat() != targetFormat) {
                result.add(canvas);
            }
        } else if (node instanceof WzImageProperty prop && prop.isListProperty()) {
            List<? extends WzObject> children = prop.getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvases(child, result, excludedNames, targetFormat);
                }
            }
        } else if (node instanceof WzImage) {
            List<? extends WzObject> children = ((WzImage) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvases(child, result, excludedNames, targetFormat);
                }
            }
        } else if (node instanceof WzDirectory) {
            List<? extends WzObject> children = ((WzDirectory) node).getChildren();
            if (children != null) {
                for (WzObject child : children) {
                    collectCanvases(child, result, excludedNames, targetFormat);
                }
            }
        }
    }
}
