package orange.wz.gui.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.FileDialog;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageFile;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngProperty;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.tools.wzkey.WzKey;
import orange.wz.provider.tools.wzkey.WzKeyStorage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
public final class Outlink {
    private static final int TASKS_PER_BATCH = 2000;

    static class IndexInfo {
        String canvasDirPath;
        Map<String, String> pathToCanvas;
        
        IndexInfo() {
            pathToCanvas = new TreeMap<>();
        }
    }

    private static IndexInfo indexInfo = null;
    private static String lastIndexPath = null;
    private static final WzKeyStorage wzKeyStorage = new WzKeyStorage();

    record Data(WzCanvasProperty object, List<String> path) {
    }

    record SourcePayload(WzPngProperty.CompressedPngData compressedPngData) {
    }

    record TaskLoadResult(Data task, String cacheKey, String fullPath) {
    }

    record FailureItem(String path, String reason) {
    }

    record BatchLoadResult(String canvasName,
                           Map<String, SourcePayload> sourceCache,
                           List<TaskLoadResult> readyTasks,
                           List<FailureItem> failures) {
    }

    public static boolean replace(List<WzObject> objects) {
        log.info("========================================");
        log.info("开始处理Outlink，传入的objects:");
        for (WzObject obj : objects) {
            log.info("  - {} ({})", obj.getName(), obj.getClass().getSimpleName());
        }
        log.info("========================================");
        
        indexInfo = null;
        lastIndexPath = null;

        File canvasDir = findCanvasDirectory(objects.getFirst());
        if (canvasDir == null) {
            canvasDir = findSubCanvasDirectory(new File("."));
            if (canvasDir == null) {
                File selected = FileDialog.chooseOpenFolder("请选择包含Canvas的文件夹");
                if (selected != null) {
                    canvasDir = findSubCanvasDirectory(selected);
                    if (canvasDir == null) canvasDir = selected;
                }
            }
        }

        if (canvasDir == null) {
            MainFrame.getInstance().setStatusText("找不到Canvas目录！");
            return false;
        }

        loadOrBuildIndex(objects.getFirst(), canvasDir);

        if (indexInfo == null || indexInfo.pathToCanvas == null || indexInfo.pathToCanvas.isEmpty()) {
            MainFrame.getInstance().setStatusText("Canvas索引为空！");
            return false;
        }

        Map<String, List<Data>> collector = new HashMap<>();
        collect(collector, objects);

        log.info("========================================");
        log.info("Collect完成，总共收集了 {} 个img：", collector.size());
        for (var entry : collector.entrySet()) {
            String imgName = entry.getKey();
            String canvasFile = indexInfo.pathToCanvas.get(imgName);
            log.info("  - {} (Canvas: {}): 包含 {} 个outlink节点", imgName, canvasFile != null ? canvasFile : "未找到", entry.getValue().size());
            for (Data data : entry.getValue()) {
                log.info("    -> {}", String.join("/", data.path()));
            }
        }
        log.info("========================================");

        if (collector.isEmpty()) {
            MainFrame.getInstance().setStatusText("没有发现需要修补的outlink！");
            return true;
        }

        int total = collector.values().stream().mapToInt(List::size).sum();
        replace(collector, total, canvasDir);
        return true;
    }

    private static void loadOrBuildIndex(WzObject wzObject, File canvasDir) {
        String indexPath = getIndexPath(wzObject);
        if (indexPath == null) return;

        if (lastIndexPath != null && lastIndexPath.equals(indexPath) && indexInfo != null &&
                indexInfo.pathToCanvas != null && !indexInfo.pathToCanvas.isEmpty()) {
            if (indexInfo.canvasDirPath != null && indexInfo.canvasDirPath.equals(canvasDir.getAbsolutePath())) {
                log.info("使用已有的索引");
                return;
            }
        }

        lastIndexPath = indexPath;

        File indexFile = new File(indexPath);
        boolean hasValidIndex = false;
        if (indexFile.exists()) {
            try {
                Gson gson = new Gson();
                FileReader reader = new FileReader(indexFile);
                Type type = new TypeToken<IndexInfo>(){}.getType();
                indexInfo = gson.fromJson(reader, type);
                reader.close();
                log.info("索引加载成功: {}", indexPath);

                if (indexInfo != null && indexInfo.pathToCanvas != null && !indexInfo.pathToCanvas.isEmpty() &&
                    indexInfo.canvasDirPath != null && indexInfo.canvasDirPath.equals(canvasDir.getAbsolutePath())) {
                    hasValidIndex = true;
                } else {
                    log.warn("索引数据为空或路径不匹配，重新生成");
                    indexInfo = null;
                }
            } catch (Exception e) {
                log.warn("加载索引失败，重新生成: {}", e.getMessage());
                indexInfo = null;
            }
        }

        if (!hasValidIndex) {
            buildIndex(canvasDir, indexFile);
        }
    }

    private static void buildIndex(File canvasDir, File indexFile) {
        MainFrame.getInstance().setStatusTextDirect("正在生成Canvas索引...");
        log.info("开始生成索引...");
        indexInfo = new IndexInfo();
        indexInfo.canvasDirPath = canvasDir.getAbsolutePath();

        List<File> wzFiles = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(canvasDir.toPath())) {
            for (Path path : pathStream.toList()) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".wz")) {
                    wzFiles.add(path.toFile());
                }
            }
        } catch (Exception e) {
            log.error("遍历Canvas目录错误", e);
            MainFrame.getInstance().setStatusText("遍历Canvas目录错误：" + e.getMessage());
            return;
        }

        if (wzFiles.isEmpty()) {
            MainFrame.getInstance().setStatusText("在" + canvasDir.getAbsolutePath() + "找不到Canvas wz文件！");
            return;
        }

        WzKey latestKey = getLatestKey();
        int current = 0;
        int total = wzFiles.size();

        for (File wzFile : wzFiles) {
            current++;
            MainFrame.getInstance().setStatusTextDirect("正在解析 " + wzFile.getName() + " (" + current + "/" + total + ")...");
            MainFrame.getInstance().updateProgress(current, total);
            log.info("正在索引: {}", wzFile.getName());

            WzFile wz = null;
            try {
                wz = new WzFile(wzFile.getAbsolutePath(), (short) -1, latestKey.getName(), latestKey.getIv(), latestKey.getUserKey());
                if (wz.parse() && wz.getWzDirectory() != null) {
                    String canvasName = wzFile.getName();
                    indexDirectory(wz.getWzDirectory(), new ArrayList<>(), canvasName);
                }
            } catch (Exception e) {
                log.warn("解析Canvas错误: {}", wzFile.getName(), e);
            } finally {
                // 索引生成时也要及时释放！
                if (wz != null) {
                    wz.clear();
                    wz = null;
                }
                System.gc();
            }
        }

        try {
            Gson gson = new Gson();
            FileWriter writer = new FileWriter(indexFile);
            gson.toJson(indexInfo, writer);
            writer.close();
            log.info("索引保存成功: {}", indexFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("保存索引失败: {}", e.getMessage());
        }

        MainFrame.getInstance().setStatusTextDirect("索引生成完成！");
    }

    private static void indexDirectory(WzDirectory dir, List<String> parentPath, String canvasName) {
        for (WzObject child : dir.getChildren()) {
            List<String> currentPath = new ArrayList<>(parentPath);
            currentPath.add(child.getName());
            String pathKey = String.join("/", currentPath);
            indexInfo.pathToCanvas.put(pathKey, canvasName);

            if (child instanceof WzImage img) {
                if (img.parse()) {
                    indexImage(img, currentPath, canvasName);
                }
            } else if (child instanceof WzDirectory subDir) {
                indexDirectory(subDir, currentPath, canvasName);
            }
        }
    }

    private static void indexImage(WzImage img, List<String> parentPath, String canvasName) {
        for (WzObject child : img.getChildren()) {
            List<String> currentPath = new ArrayList<>(parentPath);
            currentPath.add(child.getName());
            String pathKey = String.join("/", currentPath);
            indexInfo.pathToCanvas.put(pathKey, canvasName);

            if (child instanceof WzImageProperty prop) {
                indexProperty(prop, currentPath, canvasName, 1);
            }
        }
    }

    private static void indexProperty(WzImageProperty prop, List<String> parentPath, String canvasName, int currentDepth) {
        // 只索引到img下面第二层级（currentDepth==1就是第二级，索引这级就够了）
        if (currentDepth > 2) {
            return;
        }
        
        for (WzObject child : prop.getChildren()) {
            List<String> currentPath = new ArrayList<>(parentPath);
            currentPath.add(child.getName());
            String pathKey = String.join("/", currentPath);
            indexInfo.pathToCanvas.put(pathKey, canvasName);

            if (child instanceof WzImageProperty subProp) {
                indexProperty(subProp, currentPath, canvasName, currentDepth + 1);
            }
        }
    }

    private static File findCanvasDirectory(WzObject wzObject) {
        String sourceFilePath = getSourceFilePath(wzObject);
        if (sourceFilePath == null) return null;

        File parentDir = new File(sourceFilePath).getParentFile();
        if (parentDir == null) return null;

        File canvasDir = new File(parentDir, "_Canvas");
        if (canvasDir.exists() && canvasDir.isDirectory()) {
            return canvasDir;
        }

        return findSubCanvasDirectory(parentDir);
    }

    private static File findSubCanvasDirectory(File dir) {
        File[] files = dir.listFiles(f -> f.isDirectory() && (f.getName().startsWith("_Canvas") || f.getName().contains("Canvas")));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }

    private static String getIndexPath(WzObject wzObject) {
        String sourceFilePath = getSourceFilePath(wzObject);
        if (sourceFilePath == null) return null;

        File file = new File(sourceFilePath);
        String dirPath = file.getParent();
        return Path.of(dirPath, "info.json").toString();
    }

    private static String getSourceFilePath(WzObject wzObject) {
        WzFile wzFile = getWzFile(wzObject);
        if (wzFile != null) {
            return wzFile.getFilePath();
        }
        WzImageFile wzImageFile = getWzImageFile(wzObject);
        return wzImageFile == null ? null : wzImageFile.getFilePath();
    }

    private static WzFile getWzFile(WzObject wzObject) {
        if (wzObject instanceof WzFile wz) {
            return wz;
        } else if (wzObject instanceof WzDirectory wzDir) {
            return wzDir.getWzFile();
        } else if (wzObject instanceof WzImage wzImg) {
            return getWzFile(wzImg.getParent());
        } else if (wzObject instanceof WzImageProperty property) {
            return getWzFile(property.getWzImage());
        }
        return null;
    }

    private static WzImageFile getWzImageFile(WzObject wzObject) {
        if (wzObject instanceof WzImageFile wzImageFile) {
            return wzImageFile;
        } else if (wzObject instanceof WzImage wzImg) {
            return getWzImageFile(wzImg.getParent());
        } else if (wzObject instanceof WzImageProperty property) {
            return getWzImageFile(property.getWzImage());
        }
        return null;
    }

    private static void collect(Map<String, List<Data>> collector, List<? extends WzObject> objects) {
        for (WzObject wzObject : objects) {
            if (wzObject instanceof WzCanvasProperty property) {
                List<String> path = getOutlinkString(property);
                if (path == null || path.isEmpty()) continue;

                String imgName = null;
                for (String pathStr : path) {
                    if (pathStr.endsWith(".img")) {
                        imgName = pathStr;
                        break;
                    }
                }
                if (imgName == null) continue;

                List<Data> dataList = collector.computeIfAbsent(imgName, k -> new ArrayList<>());
                dataList.add(new Data(property, path));
            } else if (wzObject instanceof WzDirectory wzDir) {
                collect(collector, wzDir.getChildren());
            } else if (wzObject instanceof WzImage wzImg) {
                if (!wzImg.parse()) {
                    MainFrame.getInstance().setStatusText("文件 %s 解析失败: %s", wzImg.getName(), wzImg.getStatus().getMessage());
                    throw new RuntimeException();
                }
                collect(collector, wzImg.getChildren());
            } else if (wzObject instanceof WzImageProperty property && property.isListProperty()) {
                collect(collector, property.getChildren());
            }
        }
    }

    private static List<String> getOutlinkString(WzImageProperty property) {
        WzImageProperty outlinkNode = property.getChild("_outlink");
        if (outlinkNode == null) {
            return null;
        }

        String outlink = ((WzStringProperty) outlinkNode).getValue();

        List<String> outlinkPaths =
                Arrays.stream(outlink.split("/"))
                        .map(p -> {
                            if (p.equals("??")) return "碟喻";
                            if (p.equals("奢辨_??00")) return "奢辨_碟喻00";
                            return p;
                        })
                        .collect(Collectors.toList());
        int rootIndex = outlinkPaths.indexOf("_Canvas") + 1;
        if (rootIndex == 0) {
            return new ArrayList<>();
        }

        return outlinkPaths.subList(rootIndex, outlinkPaths.size());
    }

    private static void replace(Map<String, List<Data>> collector, int total, File canvasDir) {
        AtomicInteger current = new AtomicInteger(0);
        WzKey latestKey = getLatestKey();
        List<FailureItem> failedItems = new ArrayList<>();

        Map<String, List<Data>> tasksByCanvas = buildTasksByCanvas(collector, failedItems);
        for (FailureItem ignored : failedItems) {
            int cur = current.incrementAndGet();
            MainFrame.getInstance().updateProgress(cur, total);
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();
        int threadCount = Math.max(1, Math.min(20, (int) (cpuCores * 0.8)));
        log.info("CPU核心数: {}, 分配线程数: {}", cpuCores, threadCount);
        log.info("Outlink開始固定批次修補，每批 {} 個任務，總任務 {}", TASKS_PER_BATCH, total);

        ExecutorService writeExecutor = Executors.newFixedThreadPool(threadCount);
        int batchNo = 1;
        try {
            for (var canvasEntry : tasksByCanvas.entrySet()) {
                String canvasName = canvasEntry.getKey();
                List<Data> canvasTasks = canvasEntry.getValue();
                WzFile wz = null;
                try {
                    File canvasWzFile = new File(canvasDir, canvasName);
                    if (!canvasWzFile.exists()) {
                        for (Data data : canvasTasks) {
                            failedItems.add(new FailureItem(String.join("/", data.path()), "Canvas文件不存在: " + canvasName));
                            int cur = current.incrementAndGet();
                            MainFrame.getInstance().updateProgress(cur, total);
                        }
                        continue;
                    }

                    log.info("开始读取Canvas文件: {}", canvasName);
                    wz = new WzFile(canvasWzFile.getAbsolutePath(), (short) -1, latestKey.getName(), latestKey.getIv(), latestKey.getUserKey());
                    if (!wz.parse() || wz.getWzDirectory() == null) {
                        for (Data data : canvasTasks) {
                            failedItems.add(new FailureItem(String.join("/", data.path()), "Canvas解析失败: " + canvasName));
                            int cur = current.incrementAndGet();
                            MainFrame.getInstance().updateProgress(cur, total);
                        }
                        continue;
                    }

                    Map<String, WzImage> imageIndex = buildImageIndex(wz.getWzDirectory());
                    for (int batchStart = 0; batchStart < canvasTasks.size(); batchStart += TASKS_PER_BATCH, batchNo++) {
                        int batchEnd = Math.min(batchStart + TASKS_PER_BATCH, canvasTasks.size());
                        List<Data> batchTasks = canvasTasks.subList(batchStart, batchEnd);
                        MainFrame.getInstance().setStatusTextDirect("正在读取第 " + batchNo + " 批资源 (" + batchTasks.size() + "个任务)...");

                        BatchLoadResult loadResult = loadBatchSources(batchTasks, imageIndex, canvasName);
                        if (!loadResult.failures().isEmpty()) {
                            for (FailureItem item : loadResult.failures()) {
                                failedItems.add(item);
                                int cur = current.incrementAndGet();
                                MainFrame.getInstance().updateProgress(cur, total);
                            }
                        }

                        if (!loadResult.readyTasks().isEmpty()) {
                            List<FailureItem> writeFailures = processBatchWrites(
                                    loadResult.readyTasks(),
                                    loadResult.sourceCache(),
                                    current,
                                    total,
                                    writeExecutor
                            );
                            failedItems.addAll(writeFailures);
                        }

                        loadResult.sourceCache().clear();
                        loadResult.readyTasks().clear();
                    }
                } finally {
                    if (wz != null) {
                        wz.clear();
                        wz = null;
                        log.info("Canvas文件读取完成并已释放: {}", canvasName);
                    }
                    // 降低 Full GC 频率，按 Canvas 维度回收
                    System.gc();
                }
            }
        } finally {
            writeExecutor.shutdown();
            try {
                boolean finished = writeExecutor.awaitTermination(10, TimeUnit.MINUTES);
                if (!finished) {
                    log.warn("Outlink写入线程池关闭超时！");
                }
            } catch (InterruptedException e) {
                log.error("Outlink写入线程池关闭被中断！", e);
                Thread.currentThread().interrupt();
            }
        }

        if (!failedItems.isEmpty()) {
            log.error("========================================");
            log.error("Outlink失败任务: {} 个", failedItems.size());
            for (FailureItem item : failedItems) {
                log.error("  - {} | {}", item.path(), item.reason());
            }
            log.error("========================================");
        }

        int successCount = total - failedItems.size();
        if (current.get() != total) {
            log.error("Outlink任务计数不一致：total={}, 已处理={}", total, current.get());
        }
        log.info("Outlink完成！成功修补：{}，失败：{}", successCount, failedItems.size());
        MainFrame.getInstance().setStatusTextDirect("Outlink修补完成！成功：" + successCount + "/" + total);
    }

    private static Map<String, List<Data>> buildTasksByCanvas(Map<String, List<Data>> collector, List<FailureItem> failures) {
        Map<String, List<Data>> tasksByCanvas = new TreeMap<>();
        for (var entry : collector.entrySet()) {
            String imgName = entry.getKey();
            String canvasName = indexInfo.pathToCanvas.get(imgName);
            if (canvasName == null) {
                for (Data data : entry.getValue()) {
                    failures.add(new FailureItem(String.join("/", data.path()), "索引中找不到对应 canvas 文件"));
                }
                continue;
            }
            tasksByCanvas.computeIfAbsent(canvasName, k -> new ArrayList<>()).addAll(entry.getValue());
        }
        return tasksByCanvas;
    }

    private static BatchLoadResult loadBatchSources(List<Data> batchTasks, Map<String, WzImage> imageIndex, String canvasName) {
        Map<String, List<Data>> tasksByImg = new HashMap<>();
        List<FailureItem> loadFailures = new ArrayList<>();
        for (Data data : batchTasks) {
            String imgName = findImgName(data.path());
            String fullPath = String.join("/", data.path());
            if (imgName == null) {
                loadFailures.add(new FailureItem(fullPath, "路径中找不到 .img 节点"));
                continue;
            }
            tasksByImg.computeIfAbsent(imgName, k -> new ArrayList<>()).add(data);
        }

        Map<String, SourcePayload> sourceCache = new HashMap<>();
        List<TaskLoadResult> readyTasks = new ArrayList<>();

        for (var imgEntry : tasksByImg.entrySet()) {
            String imgName = imgEntry.getKey();
            List<Data> tasks = imgEntry.getValue();
            WzImage image = null;
            try {
                image = imageIndex.get(imgName);
                if (image == null || !image.parse()) {
                    for (Data data : tasks) {
                        loadFailures.add(new FailureItem(String.join("/", data.path()), "找不到或无法解析来源img: " + imgName));
                    }
                    continue;
                }

                for (Data data : tasks) {
                    String fullPath = String.join("/", data.path());
                    String pathKey = fullPath;
                    WzCanvasProperty from = resolveSourceCanvas(image, imgName, data.path());
                    if (from == null) {
                        List<String> fallback = fallbackPath(data.path(), imgName);
                        WzCanvasProperty fallbackFrom = resolveSourceCanvas(image, imgName, fallback);
                        if (fallbackFrom != null) {
                            pathKey = String.join("/", fallback);
                            from = fallbackFrom;
                        }
                    }
                    if (from == null) {
                        loadFailures.add(new FailureItem(fullPath, "来源图片节点不存在"));
                        continue;
                    }

                    if (!sourceCache.containsKey(pathKey)) {
                        WzPngProperty.CompressedPngData compressedData = from.exportCompressedPngData();
                        if (compressedData == null || compressedData.getCompressedBytes() == null) {
                            loadFailures.add(new FailureItem(fullPath, "来源压缩图片数据为空"));
                            continue;
                        }
                        sourceCache.put(pathKey, new SourcePayload(compressedData));
                    }
                    readyTasks.add(new TaskLoadResult(data, pathKey, fullPath));
                }
            } catch (Exception e) {
                for (Data data : tasks) {
                    loadFailures.add(new FailureItem(String.join("/", data.path()), "读取来源异常: " + e.getMessage()));
                }
            } finally {
                if (image != null) {
                    // 同一个 Canvas 需要跨批次复用时，不能 clear()（会把 reader 置空）
                    // 这里只做反解析释放子节点内存，保留 reader 供后续批次继续读取
                    image.unparse();
                }
            }
        }
        return new BatchLoadResult(canvasName, sourceCache, readyTasks, loadFailures);
    }

    private static List<FailureItem> processBatchWrites(List<TaskLoadResult> readyTasks,
                                                        Map<String, SourcePayload> sourceCache,
                                                        AtomicInteger current,
                                                        int total,
                                                        ExecutorService executor) {
        if (readyTasks.isEmpty()) return new ArrayList<>();

        ConcurrentLinkedQueue<FailureItem> writeFailures = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>(readyTasks.size());
        for (TaskLoadResult taskResult : readyTasks) {
            Future<?> future = executor.submit(() -> {
                try {
                    SourcePayload payload = sourceCache.get(taskResult.cacheKey());
                    if (payload == null || payload.compressedPngData() == null) {
                        writeFailures.add(new FailureItem(taskResult.fullPath(), "批次快取中找不到来源压缩图片"));
                    } else {
                        WzCanvasProperty to = taskResult.task().object();
                        synchronized (to) {
                            to.copyPngFromCompressedData(payload.compressedPngData(), false);
                            to.removeChild("_outlink");
                            // 不保留目标图片解码缓存，避免内存累计
                            to.clearImage();
                        }
                    }
                } catch (Exception e) {
                    writeFailures.add(new FailureItem(taskResult.fullPath(), "写入目标异常: " + e.getMessage()));
                } finally {
                    int cur = current.incrementAndGet();
                    MainFrame.getInstance().updateProgress(cur, total);
                    MainFrame.getInstance().setStatusTextDirect("正在修补 " + cur + "/" + total + "...");
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                writeFailures.add(new FailureItem("UNKNOWN", "写入线程异常: " + e.getMessage()));
            }
        }
        return new ArrayList<>(writeFailures);
    }

    private static Map<String, WzImage> buildImageIndex(WzDirectory rootDir) {
        Map<String, WzImage> index = new HashMap<>();
        if (rootDir == null) {
            return index;
        }
        buildImageIndexRecursive(rootDir, index);
        return index;
    }

    private static void buildImageIndexRecursive(WzDirectory dir, Map<String, WzImage> index) {
        for (WzObject child : dir.getChildren()) {
            if (child instanceof WzImage image) {
                index.putIfAbsent(image.getName(), image);
            } else if (child instanceof WzDirectory subDir) {
                buildImageIndexRecursive(subDir, index);
            }
        }
    }

    private static int findStep(List<String> paths, String imgName) {
        for (int i = 0; i < paths.size(); i++) {
            if (imgName.equals(paths.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String findImgName(List<String> paths) {
        for (String part : paths) {
            if (part != null && part.endsWith(".img")) {
                return part;
            }
        }
        return null;
    }

    private static List<String> fallbackPath(List<String> original, String imgName) {
        if (original == null || original.size() <= 1) {
            return null;
        }
        List<String> fallback = new ArrayList<>(original);
        fallback.remove(fallback.size() - 1);
        int step = findStep(fallback, imgName);
        return step < 0 ? null : fallback;
    }

    private static WzCanvasProperty resolveSourceCanvas(WzImage image, String imgName, List<String> path) {
        if (image == null || path == null || path.isEmpty()) {
            return null;
        }
        int step = findStep(path, imgName);
        if (step < 0) {
            return null;
        }
        return getCanvasProperty(image.getChildren(), path, step);
    }

    private static WzImage findImageInDirectory(WzDirectory dir, String imageName) {
        for (WzObject child : dir.getChildren()) {
            if (child instanceof WzImage wzImg && wzImg.getName().equals(imageName)) {
                return wzImg;
            }
            if (child instanceof WzDirectory subDir) {
                WzImage img = findImageInDirectory(subDir, imageName);
                if (img != null) return img;
            }
        }
        return null;
    }

    private static WzCanvasProperty getCanvasProperty(List<? extends WzObject> objects, List<String> path, int step) {
        for (WzObject wzObject : objects) {
            if (wzObject.getName().equals(path.get(step))) {
                if (step == path.size() - 1 && wzObject instanceof WzCanvasProperty canvas) {
                    return canvas;
                }
                step++;
                return getCanvasProperty(((WzImageProperty) wzObject).getChildren(), path, step);
            }
        }
        return null;
    }

    private static WzKey getLatestKey() {
        WzKey key = wzKeyStorage.findByName("新版本客户端");
        if (key != null) return key;
        
        List<WzKey> all = wzKeyStorage.loadAll();
        if (!all.isEmpty()) {
            return all.get(all.size() - 1);
        }
        
        return wzKeyStorage.loadAll().get(0);
    }
}
