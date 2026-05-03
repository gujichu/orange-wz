package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.canvas.AnimationFrame;
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
import java.util.regex.Pattern;

/**
 * 图片收集工具
 */
@Slf4j
public class ImagePreviewCollector {
    
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    
    /**
     * 从节点收集图片预览数据
     */
    public static ImagePreviewData collect(WzObject root) {
        ImagePreviewData result = new ImagePreviewData();
        result.setRootNode(root);
        collectRecursive(root, result, "");
        sortPreviewData(result);
        return result;
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
    private static void collectRecursive(WzObject node, ImagePreviewData result, String parentPath) {
        String currentPath = parentPath.isEmpty() ? node.getName() : parentPath + "/" + node.getName();
        
        if (node instanceof WzListProperty listProp) {
            // 【修改1】先判断是否是图片序列
            if (isImageSequence(listProp)) {
                // 收集为动画
                ImagePreviewData.AnimationData animation = buildAnimation(listProp, currentPath);
                if (animation != null) {
                    result.getAnimations().add(animation);
                }
            } else {
                // 继续递归（即使包含其他列表，也继续深入！）
                List<WzImageProperty> children = listProp.getChildren();
                if (children != null) {
                    for (WzImageProperty child : children) {
                        collectRecursive(child, result, currentPath);
                    }
                }
            }
        } else if (node instanceof WzCanvasProperty canvasProp) {
            // 单张图片
            try {
                if (canvasProp.getPngImage(true) != null) {
                    ImagePreviewData.SingleImageData image = new ImagePreviewData.SingleImageData(
                        node.getName(),
                        currentPath,
                        node, // 保存源节点引用
                        canvasProp.getPngImage(true)
                    );
                    result.getSingleImages().add(image);
                }
            } catch (Exception e) {
                log.warn("Failed to load image: {}", currentPath, e);
            }
        } else if (node instanceof WzUOLProperty uolProp) {
            // 【修改2】处理 UOL 链接（单张图片场景）
            WzCanvasProperty resolvedCanvas = resolveUolToCanvas(uolProp, currentPath);
            if (resolvedCanvas != null) {
                try {
                    ImagePreviewData.SingleImageData image = new ImagePreviewData.SingleImageData(
                        node.getName(),
                        currentPath,
                        node, // 保存 UOL 节点作为源
                        resolvedCanvas.getPngImage(true)
                    );
                    result.getSingleImages().add(image);
                } catch (Exception e) {
                    log.warn("Failed to load UOL image: {}", currentPath, e);
                }
            }
        } else if (node instanceof WzImageProperty imageProp) {
            // 其他属性类型，继续递归
            List<WzImageProperty> children = imageProp.getChildren();
            if (children != null) {
                for (WzImageProperty child : children) {
                    collectRecursive(child, result, currentPath);
                }
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
    
    /**
     * 【新增】解析 UOL 链接并获取图片（带安全检查）
     */
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
            // 检查图片是否有效
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
    
    /**
     * 判断是否是图片序列
     */
    private static boolean isImageSequence(WzListProperty listProp) {
        List<? extends WzObject> children = listProp.getChildren();
        if (children.isEmpty()) return false;
        
        int validFrameCount = 0;
        for (WzObject child : children) {
            // 只要子节点是 图片 Canvas 或者 UOL 链接，都可以认为是序列帧
            if (child instanceof WzCanvasProperty || child instanceof WzUOLProperty) {
                validFrameCount++;
            }
        }
        
        // 如果大部分是有效帧，则认为是图片序列
        double ratio = (double) validFrameCount / children.size();
        return ratio >= 0.5;
    }
    
    /**
     * 从列表构建动画数据
     */
    private static ImagePreviewData.AnimationData buildAnimation(WzListProperty listProp, String path) {
        List<AnimationFrame> frames = new ArrayList<>();
        
        for (WzObject child : listProp.getChildren()) {
            WzCanvasProperty canvasProp = null;
            WzObject frameSource = child; // 默认源节点是自己
            String framePath = path + "/" + child.getName();
            
            // 【修改3】支持 UOL 类型的帧（带安全检查）
            if (child instanceof WzUOLProperty uolProp) {
                canvasProp = resolveUolToCanvas(uolProp, framePath);
                // 重要：UOL 帧的属性（delay/origin）从目标图片节点获取，而不是 UOL 节点！
                if (canvasProp != null) {
                    frameSource = canvasProp;
                }
            } else if (child instanceof WzCanvasProperty) {
                canvasProp = (WzCanvasProperty) child;
                // 检查图片是否有效
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
            listProp, // 保存源节点引用
            frames
        );
    }
    
    /**
     * 提取延迟时间
     */
    private static int extractDelay(WzObject node) {
        if (node instanceof WzImageProperty prop) {
            WzObject delayObj = prop.getChild("delay");
            if (delayObj instanceof WzIntProperty intProp) {
                return intProp.getValue();
            }
        }
        return 60; // 默认 60ms
    }
    
    /**
     * 提取 origin 锚点
     */
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
