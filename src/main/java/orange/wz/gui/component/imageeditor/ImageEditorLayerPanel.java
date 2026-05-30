package orange.wz.gui.component.imageeditor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

final class ImageEditorLayerPanel extends JPanel {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> layerList = new JList<>(listModel);
    private ImageEditorCanvas canvas;
    private Runnable onLayersChanged;

    ImageEditorLayerPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(new TitledBorder("图层"));
        setPreferredSize(new Dimension(240, 160));

        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || canvas == null) {
                return;
            }
            ImageEditorLayerStack stack = canvas.getLayerStack();
            int topIdx = layerList.getSelectedIndex();
            if (topIdx >= 0) {
                stack.setActiveIndex(stack.indexOfTopFirst(topIdx));
            } else {
                stack.clearActiveLayer();
            }
            canvas.repaint();
        });

        JScrollPane scroll = new JScrollPane(layerList);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton upBtn = new JButton("上移");
        JButton downBtn = new JButton("下移");
        JButton visBtn = new JButton("显/隐");
        upBtn.addActionListener(e -> moveSelected(-1));
        downBtn.addActionListener(e -> moveSelected(1));
        visBtn.addActionListener(e -> toggleVisible());
        btns.add(upBtn);
        btns.add(downBtn);
        btns.add(visBtn);
        add(btns, BorderLayout.SOUTH);
    }

    void bind(ImageEditorCanvas canvas, Runnable onLayersChanged) {
        this.canvas = canvas;
        this.onLayersChanged = onLayersChanged;
        refresh();
    }

    void refresh() {
        listModel.clear();
        if (canvas == null || canvas.getLayerStack().isEmpty()) {
            return;
        }
        ImageEditorLayerStack stack = canvas.getLayerStack();
        for (ImageEditorLayer layer : stack.getLayersViewTopFirst()) {
            String prefix = layer.isVisible() ? "👁 " : "○ ";
            String active = layer == stack.getActiveLayer() ? "▸ " : "  ";
            listModel.addElement(active + prefix + layer.getName());
        }
        int topIdx = stack.hasActiveLayer() ? stack.topFirstIndexOf(stack.getActiveIndex()) : -1;
        if (topIdx >= 0 && topIdx < listModel.size()) {
            layerList.setSelectedIndex(topIdx);
        } else {
            layerList.clearSelection();
        }
    }

    private int selectedLayerIndex() {
        int topIdx = layerList.getSelectedIndex();
        if (topIdx < 0 || canvas == null) {
            return -1;
        }
        return canvas.getLayerStack().indexOfTopFirst(topIdx);
    }

    private void moveSelected(int direction) {
        if (canvas == null) {
            return;
        }
        int idx = selectedLayerIndex();
        if (idx < 0) {
            return;
        }
        ImageEditorLayerStack stack = canvas.getLayerStack();
        if (direction < 0) {
            stack.moveUp(idx);
        } else {
            stack.moveDown(idx);
        }
        canvas.repaint();
        refresh();
        notifyChanged();
        canvas.commitHistoryQuiet("调整图层顺序");
    }

    private void toggleVisible() {
        if (canvas == null) {
            return;
        }
        int idx = selectedLayerIndex();
        if (idx < 0) {
            return;
        }
        ImageEditorLayerStack stack = canvas.getLayerStack();
        ImageEditorLayer layer = stack.getLayers().get(idx);
        stack.setVisible(idx, !layer.isVisible());
        canvas.repaint();
        refresh();
        notifyChanged();
        canvas.commitHistoryQuiet("切换图层可见");
    }

    private void notifyChanged() {
        if (onLayersChanged != null) {
            onLayersChanged.run();
        }
    }
}
