package orange.wz.gui.component.canvas;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.ImagePreviewData;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片预览容器
 */
@Slf4j
public class ImagePreviewContainer extends JPanel {
    
    @Getter
    private ImagePreviewData previewData;
    private EditPane editPane;
    private final List<AnimationPreviewPanel> animationPanels = new ArrayList<>();
    
    public ImagePreviewContainer() {
        setLayout(new BorderLayout());
        setVisible(false);
    }
    
    public void setEditPane(EditPane editPane) {
        this.editPane = editPane;
    }
    
    /**
     * 设置预览数据
     */
    public void setPreviewData(ImagePreviewData data) {
        // 清理旧资源
        clear();
        
        this.previewData = data;
        
        if (data == null || !data.hasContent()) {
            setVisible(false);
            return;
        }
        
        // 创建内容面板
        JPanel contentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        
        // 添加动画
        for (ImagePreviewData.AnimationData anim : data.getAnimations()) {
            AnimationPreviewPanel panel = new AnimationPreviewPanel(anim, editPane);
            animationPanels.add(panel);
            contentPanel.add(panel);
        }
        
        // 添加单张图片
        for (ImagePreviewData.SingleImageData img : data.getSingleImages()) {
            SingleImagePreviewPanel panel = new SingleImagePreviewPanel(img, editPane);
            contentPanel.add(panel);
        }
        
        // 添加滚动
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        // 提高滚动速度
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(30);
        verticalScrollBar.setBlockIncrement(100);
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        horizontalScrollBar.setUnitIncrement(30);
        horizontalScrollBar.setBlockIncrement(100);
        
        removeAll();
        add(scrollPane, BorderLayout.CENTER);
        setVisible(true);
        revalidate();
        repaint();
    }
    
    /**
     * 清理资源
     */
    private void clear() {
        for (AnimationPreviewPanel panel : animationPanels) {
            panel.dispose();
        }
        animationPanels.clear();
        removeAll();
    }
    
    /**
     * 隐藏预览
     */
    public void hidePreview() {
        clear();
        this.previewData = null;
        setVisible(false);
    }
}
