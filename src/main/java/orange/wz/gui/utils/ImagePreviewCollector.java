package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.canvas.AnimationFrame;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzUOLProperty;
import orange.wz.provider.properties.WzVectorProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigInteger;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

/**
 * 图片收集工具
 */
@Slf4j
public class ImagePreviewCollector {

    public static final int PREVIEW_PAGE_SIZE = ImagePreviewData.PREVIEW_PAGE_SIZE;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private record RootSlice(WzObject rootRef, int start, int endExclusive) {}

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    /**
     * 从节点收集图片预览数据（等价于第 0 页）
     */
    public static ImagePreviewData collect(WzObject root) {
        return collectPage(root, 0);
    }

    /**
     * 收集指定页的预览数据（根节点下一级子节点每 {@link #PREVIEW_PAGE_SIZE} 个一页）
     */
    public static ImagePreviewData collectPage(WzObject root, int pageIndex) {
        return collectPage(root, pageIndex, AnimationPreviewConfigIni.getOptions());
    }

    /**
     * 收集指定页的预览数据，并按名称过滤选项收集动画/单图。
     */
    public static ImagePreviewData collectPage(WzObject root, int pageIndex, AnimationPreviewOptions options) {
        if (options == null) {
            options = AnimationPreviewConfigIni.getOptions();
        }
        options = options.copy();
        ImagePreviewData result = new ImagePreviewData();
        result.setRootNode(root);
        result.setPreviewPageSize(PREVIEW_PAGE_SIZE);

        int n = countPaginatableChildren(root);
        result.setPreviewChildCountAtRoot(n);

        try {
            checkInterrupted();
            if (n <= PREVIEW_PAGE_SIZE) {
                result.setPreviewPageIndex(0);
                result.setPreviewTotalPages(1);
                collectRecursive(root, result, "", null, options);
            } else {
                int totalPages = (n + PREVIEW_PAGE_SIZE - 1) / PREVIEW_PAGE_SIZE;
                result.setPreviewTotalPages(totalPages);
                int page = Math.max(0, Math.min(pageIndex, totalPages - 1));
                result.setPreviewPageIndex(page);
                int start = page * PREVIEW_PAGE_SIZE;
                int end = Math.min(start + PREVIEW_PAGE_SIZE, n);
                collectRecursive(root, result, "", new RootSlice(root, start, end), options);
            }

            sortPreviewData(result);
            result.setPreviewFilterSuffix(options.previewFilterCacheSuffix());
            return result;
        } catch (CancellationException e) {
            Thread.currentThread().interrupt();
            result.getAnimations().clear();
            result.getSingleImages().clear();
            result.setPreviewFilterSuffix(options.previewFilterCacheSuffix());
            return result;
        }
    }

    /**
     * 预览根节点直接子节点数量（与 collect 根层迭代范围一致）
     */
    public static int countPaginatableChildren(WzObject root) {
        if (root instanceof WzImage img) {
            return size(img.getChildren());
        }
        if (root instanceof WzListProperty list) {
            return size(list.getChildren());
        }
        if (root instanceof WzImageProperty prop) {
            return size(prop.getChildren());
        }
        return 0;
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 统一排序预览数据：
     * 1) 动画按帧数降序（帧数越多越靠前）
     * 2) 单图按名称升序，纯数字名称按数值升序且优先于英文名称
     */
    private static void sortPreviewData(ImagePreviewData result) {
        result.getAnimations().sort(Comparator
            .comparingInt((ImagePreviewData.AnimationData anim) ->
                anim.getFrames() == null ? 0 : anim.getFrames().size()
            )
            .reversed()
            .thenComparing(anim -> safeName(anim.getName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(anim -> safeName(anim.getName()))
        );

        result.getSingleImages().sort((a, b) -> compareNodeName(a.getName(), b.getName()));
    }

    /**
     * 递归收集
     */
    private static void collectRecursive(WzObject node, ImagePreviewData result, String parentPath, RootSlice rootSlice,
                                         AnimationPreviewOptions options) {
        checkInterrupted();
        String currentPath = parentPath.isEmpty() ? node.getName() : parentPath + "/" + node.getName();
        boolean applySlice = rootSlice != null && node == rootSlice.rootRef();

        if (node instanceof WzListProperty listProp) {
            if (isImageSequence(listProp)) {
                if (!PreviewNameFilter.shouldInclude(listProp.getName(), options)) {
                    return;
                }
                List<WzImageProperty> children = listProp.getChildren();
                if (children == null || children.isEmpty()) {
                    return;
                }
                int from = 0;
                int to = children.size();
                if (applySlice) {
                    from = rootSlice.start();
                    to = rootSlice.endExclusive();
                }
                ImagePreviewData.AnimationData animation = buildAnimationSlice(listProp, currentPath, from, to);
                if (animation != null) {
                    result.getAnimations().add(animation);
                }
            } else {
                List<WzImageProperty> children = listProp.getChildren();
                if (children == null || children.isEmpty()) {
                    return;
                }
                int from = 0;
                int to = children.size();
                if (applySlice) {
                    from = rootSlice.start();
                    to = rootSlice.endExclusive();
                }
                for (int i = from; i < to; i++) {
                    checkInterrupted();
                    collectRecursive(children.get(i), result, currentPath, null, options);
                }
            }
        } else if (node instanceof WzCanvasProperty canvasProp) {
            if (!PreviewNameFilter.shouldInclude(node.getName(), options)) {
                return;
            }
            try {
                if (canvasProp.getPngImage(true) != null) {
                    ImagePreviewData.SingleImageData image = new ImagePreviewData.SingleImageData(
                        node.getName(),
                        currentPath,
                        node,
                        canvasProp.getPngImage(true)
                    );
                    result.getSingleImages().add(image);
                }
            } catch (Exception e) {
                log.warn("Failed to load image: {}", currentPath, e);
            }
        } else if (node instanceof WzUOLProperty uolProp) {
            if (!PreviewNameFilter.shouldInclude(node.getName(), options)) {
                return;
            }
            WzCanvasProperty resolvedCanvas = resolveUolToCanvas(uolProp, currentPath);
            if (resolvedCanvas != null) {
                try {
                    ImagePreviewData.SingleImageData image = new ImagePreviewData.SingleImageData(
                        node.getName(),
                        currentPath,
                        node,
                        resolvedCanvas.getPngImage(true)
                    );
                    result.getSingleImages().add(image);
                } catch (Exception e) {
                    log.warn("Failed to load UOL image: {}", currentPath, e);
                }
            }
        } else if (node instanceof WzImage wzImage) {
            List<WzImageProperty> children = wzImage.getChildren();
            if (children == null || children.isEmpty()) {
                return;
            }
            int from = 0;
            int to = children.size();
            if (applySlice) {
                from = rootSlice.start();
                to = rootSlice.endExclusive();
            }
            for (int i = from; i < to; i++) {
                checkInterrupted();
                collectRecursive(children.get(i), result, currentPath, null, options);
            }
        } else if (node instanceof WzImageProperty imageProp) {
            List<WzImageProperty> children = imageProp.getChildren();
            if (children == null || children.isEmpty()) {
                return;
            }
            int from = 0;
            int to = children.size();
            if (applySlice) {
                from = rootSlice.start();
                to = rootSlice.endExclusive();
            }
            for (int i = from; i < to; i++) {
                checkInterrupted();
                collectRecursive(children.get(i), result, currentPath, null, options);
            }
        }
    }

    private static int compareNodeName(String left, String right) {
        String leftName = safeName(left);
        String rightName = safeName(right);

        boolean leftIsNumber = NUMBER_PATTERN.matcher(leftName).matches();
        boolean rightIsNumber = NUMBER_PATTERN.matcher(rightName).matches();

        if (leftIsNumber && rightIsNumber) {
            return new BigInteger(leftName).compareTo(new BigInteger(rightName));
        }
        if (leftIsNumber) {
            return -1;
        }
        if (rightIsNumber) {
            return 1;
        }

        int ignoreCase = String.CASE_INSENSITIVE_ORDER.compare(leftName, rightName);
        if (ignoreCase != 0) {
            return ignoreCase;
        }
        return leftName.compareTo(rightName);
    }

    private static String safeName(String name) {
        return name == null ? "" : name;
    }

    private static WzCanvasProperty resolveUolToCanvas(WzUOLProperty uolProp, String contextPath) {
        try {
            WzObject target = uolProp.getUolTarget();
            if (target == null) {
                log.warn("动画预览 - {} UOL指向节点不存在: {}", contextPath, uolProp.getValue());
                return null;
            }
            if (!(target instanceof WzCanvasProperty canvasProp)) {
                log.warn("动画预览 - {} UOL指向节点不是图片类型: {}", contextPath, uolProp.getValue());
                return null;
            }
            if (canvasProp.getPngImage(true) == null) {
                log.warn("动画预览 - {} UOL指向的图片为空: {}", contextPath, uolProp.getValue());
                return null;
            }
            return canvasProp;
        } catch (Exception e) {
            log.warn("动画预览 - {} 无法解析UOL: {}", contextPath, uolProp.getValue(), e);
            return null;
        }
    }

    private static boolean isImageSequence(WzListProperty listProp) {
        List<? extends WzObject> children = listProp.getChildren();
        if (children.isEmpty()) return false;

        int validFrameCount = 0;
        for (WzObject child : children) {
            if (child instanceof WzCanvasProperty || child instanceof WzUOLProperty) {
                validFrameCount++;
            }
        }

        double ratio = (double) validFrameCount / children.size();
        return ratio >= 0.5;
    }

    private static ImagePreviewData.AnimationData buildAnimationSlice(WzListProperty listProp, String path, int from, int to) {
        List<AnimationFrame> frames = new ArrayList<>();
        List<WzImageProperty> children = listProp.getChildren();

        for (int idx = from; idx < to; idx++) {
            checkInterrupted();
            WzObject child = children.get(idx);
            WzCanvasProperty canvasProp = null;
            WzObject frameSource = child;
            String framePath = path + "/" + child.getName();

            if (child instanceof WzUOLProperty uolProp) {
                canvasProp = resolveUolToCanvas(uolProp, framePath);
                if (canvasProp != null) {
                    frameSource = canvasProp;
                }
            } else if (child instanceof WzCanvasProperty) {
                canvasProp = (WzCanvasProperty) child;
                try {
                    if (canvasProp.getPngImage(true) == null) {
                        log.warn("动画预览 - {} 图片为空", framePath);
                        canvasProp = null;
                    }
                } catch (Exception e) {
                    log.warn("动画预览 - {} 无法加载图片", framePath, e);
                    canvasProp = null;
                }
            }

            if (canvasProp != null) {
                try {
                    int delay = extractDelay(frameSource);
                    int[] origin = extractOrigin(frameSource);
                    AnimationFrame frame = new AnimationFrame(
                        canvasProp.getPngImage(true),
                        delay,
                        origin[0],
                        origin[1]
                    );
                    frames.add(frame);
                } catch (Exception e) {
                    log.warn("动画预览 - {} 无法创建帧", framePath, e);
                }
            }
        }

        if (frames.isEmpty()) return null;

        return new ImagePreviewData.AnimationData(
            listProp.getName(),
            path,
            listProp,
            frames
        );
    }

    private static int extractDelay(WzObject node) {
        if (node instanceof WzImageProperty prop) {
            WzObject delayObj = prop.getChild("delay");
            if (delayObj instanceof WzIntProperty intProp) {
                return intProp.getValue();
            }
        }
        return 60;
    }

    private static int[] extractOrigin(WzObject node) {
        int[] origin = {0, 0};
        if (node instanceof WzImageProperty prop) {
            WzObject originObj = prop.getChild("origin");
            if (originObj instanceof WzVectorProperty vecProp) {
                origin[0] = vecProp.getX();
                origin[1] = vecProp.getY();
            }
        }
        return origin;
    }
}
