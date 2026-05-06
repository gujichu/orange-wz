package orange.wz.gui.component.panel;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@Getter
public class ImagePanel extends JPanel {
    private double zoomFactor = 1.0;
    private BufferedImage image;

    public void setZoomFactor(double zoomFactor) {
        this.zoomFactor = zoomFactor;
        revalidate();
        repaint();
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (image == null) {
            return new Dimension(320, 240);
        }
        int w = Math.max(1, (int) Math.ceil(image.getWidth() * zoomFactor));
        int h = Math.max(1, (int) Math.ceil(image.getHeight() * zoomFactor));
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int imgWidth = (int) (image.getWidth() * zoomFactor);
            int imgHeight = (int) (image.getHeight() * zoomFactor);

            int x = (getWidth() - imgWidth) / 2;
            int y = (getHeight() - imgHeight) / 2;

            g2.drawImage(image, x, y, imgWidth, imgHeight, this);

            g2.dispose();
        }
    }
}
