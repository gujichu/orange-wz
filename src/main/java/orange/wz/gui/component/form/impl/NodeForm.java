package orange.wz.gui.component.form.impl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.canvas.ImagePreviewContainer;
import orange.wz.gui.component.form.data.NodeFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.ImagePreviewCollector;
import orange.wz.gui.utils.ImagePreviewData;
import orange.wz.gui.utils.StrongCompressDialog;
import orange.wz.gui.utils.StrongCompressOptions;
import orange.wz.provider.WzObject;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@Getter
@Slf4j
public class NodeForm extends AbstractValueForm {
    
    private ImagePreviewContainer previewContainer;
    private EditPane editPane;
    private WzObject curWzObject;
    
    public NodeForm() {
        super();
        // 添加图片压缩按钮
        JButton compressBtn = new JButton("图片压缩");
        compressBtn.addActionListener(e -> compressImages());
        addButton(compressBtn);

        JButton strongCompressBtn = new JButton("强力压缩");
        strongCompressBtn.addActionListener(e -> strongCompressImages());
        addButton(strongCompressBtn);
        
        // 初始化预览容器
        previewContainer = new ImagePreviewContainer();
        valuePane.add(previewContainer, BorderLayout.CENTER);
    }
    
    public void setData(String name, String type, WzObject wzObject, EditPane editPane) {
        super.setData(name, type, wzObject, editPane);
        this.editPane = editPane;
        this.curWzObject = wzObject;
        previewContainer.setEditPane(editPane);
        
        // 检查是否有缓存的预览数据
        if (wzObject != null) {
            ImagePreviewData cachedData = editPane.getImagePreviewCache().getPreviewForNodePath(wzObject.getPath());
            if (cachedData != null) {
                log.debug("使用缓存的预览数据: {}", wzObject.getPath());
                previewContainer.setPreviewData(cachedData);
                return;
            }
        }
        
        // 没有缓存，隐藏预览
        previewContainer.hidePreview();
    }
    
    /**
     * 图片压缩：仅将当前节点下格式高于 ARGB8888 的图片改成 ARGB8888 格式
     */
    private void compressImages() {
        int confirm = JOptionPane.showConfirmDialog(
                valuePane,
                "是否将当前节点格式高于 ARGB8888 的图片压缩为 ARGB8888 格式？",
                "确认",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        if (curWzObject != null) {
            // 修改图片格式，排除特定节点
            List<String> excludedNames = List.of("icon", "iconDisabled", "iconMouseOver");
            orange.wz.gui.utils.CanvasUtil.compressImages(
                    curWzObject,
                    excludedNames,
                    editPane,
                    () -> {
                        // 重新收集并加载预览数据
                        if (editPane != null) {
                            String cacheKey = curWzObject.getPath();
                            editPane.getImagePreviewCache().removePreviewGroup(cacheKey);

                            SwingWorker<ImagePreviewData, Void> reloadWorker = new SwingWorker<>() {
                                @Override
                                protected ImagePreviewData doInBackground() {
                                    return ImagePreviewCollector.collectPage(curWzObject, 0);
                                }

                                @Override
                                protected void done() {
                                    try {
                                        ImagePreviewData newData = get();
                                        editPane.getImagePreviewCache().putPreview(newData);
                                        setPreviewData(newData);
                                    } catch (Exception ex) {
                                        log.error("Failed to reload preview", ex);
                                    }
                                }
                            };
                            reloadWorker.execute();
                        }
                    }
            );
        }
    }

    private void strongCompressImages() {
        StrongCompressOptions opts = StrongCompressDialog.showDialog(valuePane);
        if (opts == null) {
            return;
        }
        if (curWzObject == null) {
            return;
        }
        List<String> excludedNames = List.of("icon", "iconDisabled", "iconMouseOver");
        orange.wz.gui.utils.CanvasUtil.strongCompressImages(
                curWzObject,
                opts,
                excludedNames,
                editPane,
                () -> {
                    if (editPane != null) {
                        String cacheKey = curWzObject.getPath();
                        editPane.getImagePreviewCache().removePreviewGroup(cacheKey);
                        SwingWorker<ImagePreviewData, Void> reloadWorker = new SwingWorker<>() {
                            @Override
                            protected ImagePreviewData doInBackground() {
                                return ImagePreviewCollector.collectPage(curWzObject, 0);
                            }

                            @Override
                            protected void done() {
                                try {
                                    ImagePreviewData newData = get();
                                    editPane.getImagePreviewCache().putPreview(newData);
                                    setPreviewData(newData);
                                } catch (Exception ex) {
                                    log.error("Failed to reload preview after strong compress", ex);
                                }
                            }
                        };
                        reloadWorker.execute();
                    }
                }
        );
    }
    
    /**
     * 设置预览数据（缓存现在在 EditPane 中管理）
     */
    public void setPreviewData(ImagePreviewData data) {
        previewContainer.setPreviewData(data);
    }
    
    /**
     * 隐藏预览
     */
    public void hidePreview() {
        previewContainer.hidePreview();
    }

    @Override
    public NodeFormData getData() {
        return new NodeFormData(nameInput.getText(), typeInput.getText());
    }
    
    @Override
    public void onHide() {
        super.onHide();
        previewContainer.hidePreview();
    }
}
