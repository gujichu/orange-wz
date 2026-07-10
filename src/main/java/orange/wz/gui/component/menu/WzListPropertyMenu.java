package orange.wz.gui.component.menu;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.canvas.CanvasWall;
import orange.wz.gui.component.dialog.*;
import orange.wz.gui.component.form.data.*;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.*;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static orange.wz.gui.Icons.*;

@Slf4j
public final class WzListPropertyMenu extends JPopupMenu {
    private final EditPane editPane;
    private final JTree tree;
    @Getter
    private final JMenuItem deleteBtn;
    @Getter
    private final JMenuItem copyBtn;
    @Getter
    private final JMenuItem pasteBtn;

    public WzListPropertyMenu(EditPane editPane, JTree tree) {
        super();
        this.editPane = editPane;
        this.tree = tree;

        JMenu addBtn = new JMenu("子节点");
        addBtn.setIcon(AiOutlinePlus);
        JMenuItem addCanvasBtn = new JMenuItem("图片");
        JMenuItem addConvexBtn = new JMenuItem("Convex");
        JMenuItem addDoubleBtn = new JMenuItem("Double");
        JMenuItem addFloatBtn = new JMenuItem("Float");
        JMenuItem addIntBtn = new JMenuItem("Int");
        JMenuItem addListBtn = new JMenuItem("列表");
        JMenuItem addLongBtn = new JMenuItem("Long");
        JMenuItem addNullBtn = new JMenuItem("Null");
        JMenuItem addShortBtn = new JMenuItem("Short");
        JMenuItem addSoundBtn = new JMenuItem("音频");
        JMenuItem addStringBtn = new JMenuItem("字符串");
        JMenuItem addUOLBtn = new JMenuItem("链接");
        JMenuItem addVectorBtn = new JMenuItem("向量");
        addBtn.add(addCanvasBtn);
        addBtn.add(addConvexBtn);
        addBtn.add(addDoubleBtn);
        addBtn.add(addFloatBtn);
        addBtn.add(addIntBtn);
        addBtn.add(addListBtn);
        addBtn.add(addLongBtn);
        addBtn.add(addNullBtn);
        addBtn.add(addShortBtn);
        addBtn.add(addSoundBtn);
        addBtn.add(addStringBtn);
        addBtn.add(addUOLBtn);
        addBtn.add(addVectorBtn);

        copyBtn = new JMenuItem("复制", AiOutlineCopy);
        pasteBtn = new JMenuItem("粘贴", MdOutlineContentPaste);
        JMenuItem duplicateSiblingBtn = new JMenuItem("同节点复制");
        deleteBtn = new JMenuItem("删除节点", AiOutlineDelete);
        JMenu exportBtn = new JMenu("导出");
        JMenuItem exportImgBtn = new JMenuItem("Img");
        JMenuItem exportXmlBtn = new JMenuItem("Xml");
        JMenuItem exportJsonBtn = new JMenuItem("Json");
        exportBtn.add(exportImgBtn);
        exportBtn.add(exportXmlBtn);
        exportBtn.add(exportJsonBtn);
        JMenuItem chineseBtn = new JMenuItem("汉化");
        JMenuItem compareImgBtn = new JMenuItem("图片对比");
        JMenuItem imagePreviewBtn = new JMenuItem("动画预览");
        JMenuItem imageBtn = new JMenuItem("图片嗅探");
        JMenuItem outlinkBtn = new JMenuItem("Outlink");
        JMenuItem sicBtn = new JMenuItem("排序并改名");
        JMenuItem delChild = new JMenuItem("批量删除");
        JMenuItem batchModify = new JMenuItem("批量修改");
        JMenuItem changeCavFmt = new JMenuItem("图片格式");
        JMenuItem scaleImage = new JMenuItem("图片缩放");
        JMenuItem imageSize = new JMenuItem("图片大小");
        JMenuItem changeNodeName = new JMenuItem("修改节点名");

        addCanvasBtnItem(addCanvasBtn);
        addConvexBtnItem(addConvexBtn);
        addDoubleBtnItem(addDoubleBtn);
        addFloatBtnItem(addFloatBtn);
        addIntBtnItem(addIntBtn);
        addListBtnItem(addListBtn);
        addLongBtnItem(addLongBtn);
        addNullBtnItem(addNullBtn);
        addShortBtnItem(addShortBtn);
        addSoundBtnItem(addSoundBtn);
        addStringBtnItem(addStringBtn);
        addUOLBtnItem(addUOLBtn);
        addVectorBtnItem(addVectorBtn);
        copyBtn.addActionListener(e -> editPane.doCopy());
        pasteBtn.addActionListener(e -> editPane.doPaste());
        duplicateSiblingBtn.addActionListener(e -> editPane.doDuplicateSibling());
        deleteBtnAction(deleteBtn);
        addExportImgBtnAction(exportImgBtn);
        addExportXmlBtnAction(exportXmlBtn);
        addExportJsonBtnAction(exportJsonBtn);
        addChineseBtnAction(chineseBtn);
        compareImgBtn.addActionListener(e -> editPane.compareImg());
        addImagePreviewBtnAction(imagePreviewBtn);
        addImageBtnAction(imageBtn);
        addOutlinkBtnAction(outlinkBtn);
        sicBtn.addActionListener(e -> editPane.sortAndReindexChildren());
        delChild.addActionListener(e -> editPane.removeAllWzChildWithName());
        batchModify.addActionListener(e -> editPane.openBatchModify());
        changeCavFmt.addActionListener(e -> editPane.changeCavFmt());
        scaleImage.addActionListener(e -> editPane.scaleImage());
        imageSize.addActionListener(e -> editPane.resizeImageSize());
        changeNodeName.addActionListener(e -> editPane.changeNodeName());

        add(addBtn);
        add(copyBtn);
        add(pasteBtn);
        add(duplicateSiblingBtn);
        add(deleteBtn);
        add(exportBtn);
        add(chineseBtn);
        add(compareImgBtn);
        add(imagePreviewBtn);
        add(imageBtn);
        add(outlinkBtn);
        add(sicBtn);
        add(delChild);
        add(batchModify);
        add(changeCavFmt);
        add(scaleImage);
        add(imageSize);
        add(changeNodeName);
    }

    private void addExportImgBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            editPane.exportImg(selectedPaths);
        });
    }

    private void addExportXmlBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            editPane.exportXml(selectedPaths);
        });
    }

    private void addExportJsonBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            editPane.exportJson(selectedPaths);
        });
    }

    private void deleteBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            for (TreePath treePath : selectedPaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                WzObject wzObject = (WzObject) node.getUserObject();
                WzObject pWzObject = wzObject.getParent();

                if (pWzObject instanceof WzImage image && image.removeChild(wzObject.getName())) {
                    editPane.removeNodeFromTree((DefaultMutableTreeNode) treePath.getLastPathComponent());
                } else if (pWzObject instanceof WzImageProperty property && property.removeChild(wzObject.getName())) {
                    editPane.removeNodeFromTree((DefaultMutableTreeNode) treePath.getLastPathComponent());
                } else {
                    log.error("无法删除节点, 父节点类型: {}", pWzObject.getClass().getSimpleName());
                }
            }
            editPane.resetValueForm();
        });
    }

    private void addCanvasBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            CanvasDialog nodeDialog = new CanvasDialog("新增 Canvas", editPane);
            CanvasFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzCanvasProperty prop = new WzCanvasProperty(name, imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }
            prop.initPngProperty(name, prop, imageProperty.getWzImage());
            prop.setPng(data.getValue(), data.getFormat(), data.getScale());

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addConvexBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            NodeDialog nodeDialog = new NodeDialog("新增 Convex", editPane);
            NodeFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzConvexProperty prop = new WzConvexProperty(name, imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addDoubleBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            DoubleDialog nodeDialog = new DoubleDialog("新增 Double", editPane);
            DoubleFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzDoubleProperty prop = new WzDoubleProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addFloatBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            FloatDialog nodeDialog = new FloatDialog("新增 Float", editPane);
            FloatFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzFloatProperty prop = new WzFloatProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addIntBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            IntDialog nodeDialog = new IntDialog("新增 Int", editPane);
            IntFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzIntProperty prop = new WzIntProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addListBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            NodeDialog nodeDialog = new NodeDialog("新增 List", editPane);
            NodeFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzListProperty prop = new WzListProperty(name, imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addLongBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            LongDialog nodeDialog = new LongDialog("新增 Long", editPane);
            LongFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzLongProperty prop = new WzLongProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addNullBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            NodeDialog nodeDialog = new NodeDialog("新增 Null", editPane);
            NodeFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzNullProperty prop = new WzNullProperty(name, imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addShortBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            ShortDialog nodeDialog = new ShortDialog("新增 Short", editPane);
            ShortFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzShortProperty prop = new WzShortProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addSoundBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            SoundDialog nodeDialog = new SoundDialog("新增 Sound", editPane);
            SoundFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzSoundProperty prop = new WzSoundProperty(name, imageProperty, imageProperty.getWzImage());
            prop.setSound(data.getSoundBytes());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addStringBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            StringDialog nodeDialog = new StringDialog("新增 String", editPane);
            StringFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzStringProperty prop = new WzStringProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addUOLBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            StringDialog nodeDialog = new StringDialog("新增 UOL", editPane);
            StringFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzUOLProperty prop = new WzUOLProperty(name, data.getValue(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addVectorBtnItem(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("不要多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();

            VectorDialog nodeDialog = new VectorDialog("新增 Vector", editPane);
            VectorFormData data = nodeDialog.getData();

            if (data == null) return;

            String name = data.getName();

            if (name.isEmpty()) {
                JMessageUtil.error("名称不能为空");
                return;
            }

            WzImageProperty imageProperty = (WzImageProperty) node.getUserObject();

            WzVectorProperty prop = new WzVectorProperty(name, data.getX(), data.getY(), imageProperty, imageProperty.getWzImage());
            if (!imageProperty.addChild(prop)) {
                JMessageUtil.error("名称已存在");
                return;
            }

            if (node.isLeaf()) return; // isLeaf 说明未加载数据，就不要插入了
            prop.setTempChanged(true);
            editPane.insertNodeToTree(node, prop, true, 0);
        });
    }

    private void addChineseBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) {
                return;
            }
            List<WzObject> roots = new ArrayList<>();
            for (TreePath treePath : selectedPaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                roots.add((WzObject) node.getUserObject());
            }
            ChineseReplaceLauncher.openFromSelection(editPane, editPane, roots);
        });
    }

    private void addImageBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            if (selectedPaths.length != 1) {
                JMessageUtil.error("该功能不支持多选");
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();
            WzImageProperty prop = (WzImageProperty) node.getUserObject();
            List<CanvasUtilData> data = new ArrayList<>();
            CanvasUtil.search(data, prop.getChildren());

            CanvasWall canvasWall = new CanvasWall(data, prop.getPath(), node, editPane);
            canvasWall.setVisible(true);
        });
    }

    private void addOutlinkBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            Instant now = Instant.now();
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return;

            List<WzObject> objects = new ArrayList<>();
            for (TreePath treePath : selectedPaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                WzObject wzObject = (WzObject) node.getUserObject();
                objects.add(wzObject);
            }

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    Outlink.replace(objects);
                    return null;
                }

                @Override
                protected void done() {
                    SwingWorkerHelper.finish(this, () -> {
                        Instant end = Instant.now();
                        MainFrame.getInstance().setStatusText("Outlink 结束，耗时 %d 秒", Duration.between(now, end).toSeconds());
                    });
                }
            };
            worker.execute();
        });
    }
    
    private void addImagePreviewBtnAction(JMenuItem item) {
        item.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null || selectedPaths.length != 1) {
                JMessageUtil.error("该功能不支持多选");
                return;
            }
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();
            WzObject wzObject = (WzObject) node.getUserObject();
            
            String cacheKey = wzObject.getPath();

            // 如果已经有缓存，**先清除**！因为用户可能编辑了节点内容，需要重新生成（含分页键 path#p*）
            editPane.getImagePreviewCache().removePreviewGroup(cacheKey);
            
            // 重新收集图片（子节点过多时仅加载第 0 页）
            SwingWorker<ImagePreviewData, Void> worker = new SwingWorker<>() {
                @Override
                protected ImagePreviewData doInBackground() {
                    return ImagePreviewCollector.collectPage(wzObject, 0, AnimationPreviewConfigIni.getOptions());
                }
                
                @Override
                protected void done() {
                    try {
                        ImagePreviewData data = get();
                        if (!data.hasContent() && !data.isPaginationActive()) {
                            JMessageUtil.warn("该节点下没有找到可预览的图片");
                            return;
                        }

                        editPane.getImagePreviewCache().putPreview(data);
                        editPane.getNodeForm().setPreviewData(data);
                    } catch (Exception ex) {
                        log.error("图片预览失败", ex);
                        JMessageUtil.error("图片预览失败: " + ex.getMessage());
                    }
                }
            };
            worker.execute();
        });
    }
}
