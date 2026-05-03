package orange.wz.gui.component.canvas;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.FileDialog;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.ImagePreviewData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 动画预览面板
 */
@Slf4j
public class AnimationPreviewPanel extends BasePreviewPanel {
    
    private final ImagePreviewData.AnimationData animationData;
    private final EditPane editPane;
    private AnimationPlayer player;
    private JButton playPauseBtn;
    
    // Origin 对齐相关
    private int originOffsetX = 0;
    private int originOffsetY = 0;
    private int canvasWidth = 0;
    private int canvasHeight = 0;
    
    // 预缓存对齐后的图片（避免每一帧都创建新的 BufferedImage）
    private final java.util.Map<AnimationFrame, BufferedImage> alignedImageCache = new java.util.HashMap<>();
    
    public AnimationPreviewPanel(ImagePreviewData.AnimationData animationData, EditPane editPane) {
        super(animationData.getName());
        this.animationData = animationData;
        this.editPane = editPane;
        
        // 计算 origin 对齐的画布尺寸
        if (!animationData.getFrames().isEmpty()) {
            int[] canvasSize = calculateCanvasSize(animationData.getFrames());
            this.canvasWidth = canvasSize[0];
            this.canvasHeight = canvasSize[1];
            this.originOffsetX = canvasSize[2];
            this.originOffsetY = canvasSize[3];
        }
        
        // 预缓存所有对齐后的图片（只创建一次！）
        for (AnimationFrame frame : animationData.getFrames()) {
            alignedImageCache.put(frame, alignWithOrigin(frame));
        }
        
        initPlayer();
        if (!animationData.getFrames().isEmpty()) {
            this.currentImage = alignedImageCache.get(animationData.getFrames().get(0));
        }
        
        setupPopupMenu();
    }
    
    private void setupPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem goToSourceItem = new JMenuItem("跳转到源节点");
        goToSourceItem.addActionListener((ActionEvent e) -> {
            if (animationData.getSourceNode() != null && editPane != null) {
                editPane.focusNodeByWzObject(animationData.getSourceNode());
            }
        });
        popupMenu.add(goToSourceItem);

        JMenuItem viewInWindowItem = new JMenuItem("窗口查看");
        viewInWindowItem.addActionListener((ActionEvent e) -> {
            LargeImageView largeImageView = new LargeImageView(animationData);
            largeImageView.setVisible(true);
        });
        popupMenu.add(viewInWindowItem);
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
    
    private void initPlayer() {
        player = new AnimationPlayer(animationData.getFrames());
        player.setFrameChangeListener(frame -> {
            if (frame != null) {
                // 使用缓存的对齐后图片，避免创建新的！
                BufferedImage cached = alignedImageCache.get(frame);
                if (cached != null) {
                    updateImage(cached);
                }
            }
        });
    }
    
    /**
     * 计算画布尺寸，基于所有帧的 origin 锚点
     * 返回 [canvasWidth, canvasHeight, originOnCanvasX, originOnCanvasY]
     * 
     * Origin 属性含义：
     * - originX, originY 表示图片中的锚点相对于图片左上角的偏移
     * - 播放动画时，所有图片的锚点应该对齐到同一个位置
     */
    private int[] calculateCanvasSize(java.util.List<AnimationFrame> frames) {
        // 计算所有图片相对于 origin 锚点的边界
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        
        for (AnimationFrame frame : frames) {
            BufferedImage img = frame.getImage();
            int originX = frame.getOriginX();
            int originY = frame.getOriginY();
            
            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();
            
            // 计算图片四个角相对于 origin 锚点的位置
            int x1 = -originX; // 图片左上角相对于 origin 的 X
            int y1 = -originY; // 图片左上角相对于 origin 的 Y
            int x2 = x1 + imgWidth; // 图片右下角相对于 origin 的 X
            int y2 = y1 + imgHeight; // 图片右下角相对于 origin 的 Y
            
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }
        
        // 计算画布大小
        int canvasWidth = maxX - minX;
        int canvasHeight = maxY - minY;
        
        // 计算 origin 点在画布上的位置
        int originOnCanvasX = -minX;
        int originOnCanvasY = -minY;
        
        return new int[]{canvasWidth, canvasHeight, originOnCanvasX, originOnCanvasY};
    }
    
    /**
     * 按照 origin 锚点对齐图片
     */
    private BufferedImage alignWithOrigin(AnimationFrame frame) {
        BufferedImage img = frame.getImage();
        
        // 计算图片在画布上的绘制位置
        // 图片的 origin 点应该对齐到画布上的 originOnCanvas 位置
        int drawX = originOffsetX - frame.getOriginX();
        int drawY = originOffsetY - frame.getOriginY();
        
        BufferedImage newImg = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newImg.createGraphics();
        
        try {
            // 禁用抗锯齿，保持原始直角边缘
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            
            // 按照 origin 锚点对齐绘制原图
            g2d.drawImage(img, drawX, drawY, null);
        } finally {
            g2d.dispose();
        }
        
        return newImg;
    }
    
    @Override
    protected void addButtons(JPanel buttonPanel) {
        playPauseBtn = new JButton("播放");
        playPauseBtn.addActionListener(e -> togglePlayPause());
        
        JButton downloadBtn = new JButton("下载");
        downloadBtn.addActionListener(e -> downloadAnimation());
        
        buttonPanel.add(playPauseBtn);
        buttonPanel.add(downloadBtn);
    }
    
    /**
     * 切换播放/暂停
     */
    private void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
            playPauseBtn.setText("播放");
        } else {
            player.play();
            playPauseBtn.setText("暂停");
        }
    }
    
    /**
     * 下载动画
     */
    private void downloadAnimation() {
        File file = FileDialog.chooseSaveFile(this, "保存动画", new File(name + ".gif"), new String[]{"gif"});
        if (file == null) return;
        
        // 后台任务生成 GIF
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 调用 GIF 生成器
                orange.wz.gui.utils.GifGenerator.generate(animationData, file);
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(AnimationPreviewPanel.this, 
                        "保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    log.error("Failed to save GIF", ex);
                    JOptionPane.showMessageDialog(AnimationPreviewPanel.this, 
                        "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        if (player != null) {
            player.dispose();
        }
        // 清理对齐后的图片缓存
        alignedImageCache.clear();
    }
}
