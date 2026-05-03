package orange.wz.gui.component.canvas;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.FileDialog;
import orange.wz.gui.utils.ImagePreviewData;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
public final class LargeImageView extends JFrame {
    private static final int IMAGE_SIZE = 800;
    private static final int BUTTON_HEIGHT = 35;

    private AnimationPlayer player;
    private JButton playPauseBtn;
    private ImagePreviewData.AnimationData animationData;
    private ImagePreviewData.SingleImageData imageData;
    private BufferedImage currentDisplayImage;
    private int canvasWidth;
    private int canvasHeight;
    private int originOffsetX;
    private int originOffsetY;
    private boolean isAnimation;

    public LargeImageView(ImagePreviewData.AnimationData animationData) {
        super("图片预览 - " + animationData.getName());
        this.animationData = animationData;
        this.isAnimation = true;

        initWindow();
        initAnimation();
        initUI();
    }

    public LargeImageView(ImagePreviewData.SingleImageData imageData) {
        super("图片预览 - " + imageData.getName());
        this.imageData = imageData;
        this.isAnimation = false;

        initWindow();
        initUI();
    }

    private void initWindow() {
        setIconImage(MainFrame.getInstance().getIconImage());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(IMAGE_SIZE + 16, IMAGE_SIZE + BUTTON_HEIGHT + 39);
        setLocationRelativeTo(null);

        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(esc, "ESC_CLOSE");
        getRootPane().getActionMap().put("ESC_CLOSE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void initAnimation() {
        if (!animationData.getFrames().isEmpty()) {
            int[] canvasSize = calculateCanvasSize(animationData.getFrames());
            this.canvasWidth = canvasSize[0];
            this.canvasHeight = canvasSize[1];
            this.originOffsetX = canvasSize[2];
            this.originOffsetY = canvasSize[3];
        }

        player = new AnimationPlayer(animationData.getFrames());
        player.setFrameChangeListener(frame -> {
            if (frame != null) {
                updateImage(alignWithOrigin(frame));
            }
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        setContentPane(mainPanel);

        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawImage(g);
            }
        };
        imagePanel.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        imagePanel.setBackground(Color.DARK_GRAY);
        mainPanel.add(imagePanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonPanel.setPreferredSize(new Dimension(IMAGE_SIZE, BUTTON_HEIGHT));
        addButtons(buttonPanel);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        if (isAnimation && !animationData.getFrames().isEmpty()) {
            currentDisplayImage = alignWithOrigin(animationData.getFrames().get(0));
        } else if (!isAnimation) {
            currentDisplayImage = imageData.getImage();
        }
    }

    private void addButtons(JPanel buttonPanel) {
        if (isAnimation) {
            playPauseBtn = new JButton("播放");
            playPauseBtn.addActionListener(e -> togglePlayPause());
            buttonPanel.add(playPauseBtn);
        }

        JButton downloadBtn = new JButton("下载");
        downloadBtn.addActionListener(e -> download());
        buttonPanel.add(downloadBtn);

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);
    }

    private void drawImage(Graphics g) {
        if (currentDisplayImage == null) return;

        int imgWidth = currentDisplayImage.getWidth();
        int imgHeight = currentDisplayImage.getHeight();

        int drawWidth, drawHeight;
        if (imgWidth > IMAGE_SIZE || imgHeight > IMAGE_SIZE) {
            double scale = Math.min(
                    (double) IMAGE_SIZE / imgWidth,
                    (double) IMAGE_SIZE / imgHeight
            );
            drawWidth = (int) (imgWidth * scale);
            drawHeight = (int) (imgHeight * scale);
        } else {
            drawWidth = imgWidth;
            drawHeight = imgHeight;
        }

        int x = (IMAGE_SIZE - drawWidth) / 2;
        int y = (IMAGE_SIZE - drawHeight) / 2;

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(currentDisplayImage, x, y, drawWidth, drawHeight, null);
    }

    private void updateImage(BufferedImage image) {
        this.currentDisplayImage = image;
        repaint();
    }

    private void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
            playPauseBtn.setText("播放");
        } else {
            player.play();
            playPauseBtn.setText("暂停");
        }
    }

    private void download() {
        if (isAnimation) {
            downloadAnimation();
        } else {
            downloadImage();
        }
    }

    private void downloadAnimation() {
        File file = FileDialog.chooseSaveFile(this, "保存动画", new File(animationData.getName() + ".gif"), new String[]{"gif"});
        if (file == null) return;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                orange.wz.gui.utils.GifGenerator.generate(animationData, file);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(LargeImageView.this, "保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    log.error("保存失败", ex);
                    JOptionPane.showMessageDialog(LargeImageView.this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void downloadImage() {
        File file = FileDialog.chooseSaveFile(this, "保存图片", new File(imageData.getName() + ".png"), new String[]{"png"});
        if (file == null) return;

        try {
            ImageIO.write(imageData.getImage(), "PNG", file);
            JOptionPane.showMessageDialog(this, "保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int[] calculateCanvasSize(List<AnimationFrame> frames) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (AnimationFrame frame : frames) {
            BufferedImage img = frame.getImage();
            int originX = frame.getOriginX();
            int originY = frame.getOriginY();

            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();

            int x1 = -originX;
            int y1 = -originY;
            int x2 = x1 + imgWidth;
            int y2 = y1 + imgHeight;

            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }

        int canvasWidth = maxX - minX;
        int canvasHeight = maxY - minY;
        int originOnCanvasX = -minX;
        int originOnCanvasY = -minY;

        return new int[]{canvasWidth, canvasHeight, originOnCanvasX, originOnCanvasY};
    }

    private BufferedImage alignWithOrigin(AnimationFrame frame) {
        BufferedImage img = frame.getImage();

        int drawX = originOffsetX - frame.getOriginX();
        int drawY = originOffsetY - frame.getOriginY();

        BufferedImage newImg = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newImg.createGraphics();

        try {
            // 禁用抗锯齿，保持原始直角边缘
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            
            g2d.drawImage(img, drawX, drawY, null);
        } finally {
            g2d.dispose();
        }

        return newImg;
    }

    @Override
    public void dispose() {
        if (player != null) {
            player.dispose();
        }
        super.dispose();
    }
}
