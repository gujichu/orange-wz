package orange.wz.gui.component.canvas;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 预览面板基类
 */
public abstract class BasePreviewPanel extends JPanel {
    
    protected static final int PREVIEW_SIZE = 200;
    protected static final Dimension PANEL_SIZE = new Dimension(PREVIEW_SIZE, PREVIEW_SIZE + 60);
    
    protected String name;
    protected BufferedImage currentImage;
    
    public BasePreviewPanel(String name) {
        this.name = name;
        setLayout(new BorderLayout());
        setPreferredSize(PANEL_SIZE);
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        initComponents();
    }
    
    protected void initComponents() {
        // 图片显示区域
        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawImage(g);
            }
        };
        imagePanel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePanel.setBackground(Color.DARK_GRAY);
        add(imagePanel, BorderLayout.CENTER);
        
        // 名称标签
        JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
        nameLabel.setToolTipText(name);
        
        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        addButtons(buttonPanel);
        
        // 底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(nameLabel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 绘制图片
     */
    protected void drawImage(Graphics g) {
        if (currentImage == null) return;
        
        int imgWidth = currentImage.getWidth();
        int imgHeight = currentImage.getHeight();
        
        // 计算缩放后尺寸
        int drawWidth, drawHeight;
        if (imgWidth > PREVIEW_SIZE || imgHeight > PREVIEW_SIZE) {
            double scale = Math.min(
                (double) PREVIEW_SIZE / imgWidth,
                (double) PREVIEW_SIZE / imgHeight
            );
            drawWidth = (int) (imgWidth * scale);
            drawHeight = (int) (imgHeight * scale);
        } else {
            drawWidth = imgWidth;
            drawHeight = imgHeight;
        }
        
        // 居中绘制
        int x = (PREVIEW_SIZE - drawWidth) / 2;
        int y = (PREVIEW_SIZE - drawHeight) / 2;
        
        g.drawImage(currentImage, x, y, drawWidth, drawHeight, null);
    }
    
    /**
     * 添加按钮（子类实现）
     */
    protected abstract void addButtons(JPanel buttonPanel);
    
    /**
     * 更新显示的图片
     */
    protected void updateImage(BufferedImage image) {
        this.currentImage = image;
        repaint();
    }
}
