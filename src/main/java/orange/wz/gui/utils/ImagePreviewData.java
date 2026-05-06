package orange.wz.gui.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import orange.wz.gui.component.canvas.AnimationFrame;
import orange.wz.provider.WzObject;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片预览数据
 */
@Data
public class ImagePreviewData {

    public static final int PREVIEW_PAGE_SIZE = 50;
    
    /**
     * 根节点引用（用于右键菜单导航）
     */
    private WzObject rootNode;
    
    /**
     * 动画列表
     */
    private List<AnimationData> animations = new ArrayList<>();
    
    /**
     * 单张图片列表
     */
    private List<SingleImageData> singleImages = new ArrayList<>();

    /**
     * 预览根节点下一级子节点数量（用于动画预览分页）
     */
    private int previewChildCountAtRoot;

    /**
     * 当前预览页（0 起）
     */
    private int previewPageIndex;

    /**
     * 每页子节点数上限
     */
    private int previewPageSize = PREVIEW_PAGE_SIZE;

    /**
     * 总页数
     */
    private int previewTotalPages = 1;

    /**
     * 是否启用分页（仅动画预览根层子节点数超过 {@link ImagePreviewCollector#PREVIEW_PAGE_SIZE} 时为 true）
     */
    public boolean isPaginationActive() {
        return previewTotalPages > 1;
    }

    /**
     * 写入缓存时使用的键（分页时为 path#p页码）
     */
    public String buildCacheKey() {
        if (rootNode == null) {
            return "";
        }
        String path = rootNode.getPath();
        if (!isPaginationActive()) {
            return path;
        }
        return path + "#p" + previewPageIndex;
    }
    
    /**
     * 是否有内容
     */
    public boolean hasContent() {
        return !animations.isEmpty() || !singleImages.isEmpty();
    }
    
    /**
     * 动画数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimationData {
        private String name;
        private String path;
        private WzObject sourceNode;
        private List<AnimationFrame> frames;
    }
    
    /**
     * 单张图片数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SingleImageData {
        private String name;
        private String path;
        private WzObject sourceNode;
        private BufferedImage image;
    }
}
