package orange.wz.gui.component.canvas;

import orange.wz.gui.component.FileDialog;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.ImagePreviewData;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

/**
 * 单张图片预览面板
 */
public class SingleImagePreviewPanel extends BasePreviewPanel {
    
    private final ImagePreviewData.SingleImageData imageData;
    private final EditPane editPane;
    
    public SingleImagePreviewPanel(ImagePreviewData.SingleImageData imageData, EditPane editPane) {
        super(imageData.getName());
        this.imageData = imageData;
        this.editPane = editPane;
        this.currentImage = imageData.getImage();
        
        setupPopupMenu();
    }
    
    private void setupPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem goToSourceItem = new JMenuItem("跳转到源节点");
        goToSourceItem.addActionListener((ActionEvent e) -> {
            if (imageData.getSourceNode() != null && editPane != null) {
                editPane.focusNodeByWzObject(imageData.getSourceNode());
            }
        });
        popupMenu.add(goToSourceItem);

        JMenuItem viewInWindowItem = new JMenuItem("窗口查看");
        viewInWindowItem.addActionListener((ActionEvent e) -> {
            LargeImageView largeImageView = new LargeImageView(imageData);
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
    
    @Override
    protected void addButtons(JPanel buttonPanel) {
        JButton downloadBtn = new JButton("下载");
        downloadBtn.addActionListener(e -> downloadImage());
        buttonPanel.add(downloadBtn);
    }
    
    /**
     * 下载图片
     */
    private void downloadImage() {
        File file = FileDialog.chooseSaveFile(this, "保存图片", new File(name + ".png"), new String[]{"png"});
        if (file == null) return;
        
        try {
            ImageIO.write(currentImage, "PNG", file);
            JOptionPane.showMessageDialog(this, "保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "保存失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
