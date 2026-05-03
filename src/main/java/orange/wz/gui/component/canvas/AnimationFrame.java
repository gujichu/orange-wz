package orange.wz.gui.component.canvas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.image.BufferedImage;

/**
 * 动画帧数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimationFrame {
    /**
     * 帧图片
     */
    private BufferedImage image;
    
    /**
     * 延迟时间（毫秒），默认 60ms
     */
    private int delayMs = 60;
    
    /**
     * origin 锚点 X 坐标
     */
    private int originX = 0;
    
    /**
     * origin 锚点 Y 坐标
     */
    private int originY = 0;
}
