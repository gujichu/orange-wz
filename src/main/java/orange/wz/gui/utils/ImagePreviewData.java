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
