package orange.wz.gui.component.imageeditor;



import javax.swing.*;

import javax.swing.border.EmptyBorder;

import java.awt.*;



final class ImageEditorHistoryPanel extends JPanel {



    private final DefaultListModel<ImageEditorHistory.Entry> model = new DefaultListModel<>();

    private final JList<ImageEditorHistory.Entry> list = new JList<>(model);

    private ImageEditorCanvas canvas;

    private Runnable onJump;



    ImageEditorHistoryPanel() {

        setLayout(new BorderLayout());

        setBorder(new EmptyBorder(4, 4, 4, 4));

        list.setFixedCellHeight(56);

        list.setCellRenderer(new HistoryCellRenderer());

        list.addListSelectionListener(e -> {

            if (e.getValueIsAdjusting()) {

                return;

            }

        });

        list.addMouseListener(new java.awt.event.MouseAdapter() {

            @Override

            public void mouseClicked(java.awt.event.MouseEvent e) {

                if (canvas == null) {

                    return;

                }

                int idx = list.locationToIndex(e.getPoint());

                if (idx >= 0) {

                    canvas.goToHistory(idx);

                    refreshSelection();

                    if (onJump != null) {

                        onJump.run();

                    }

                }

            }

        });

        JScrollPane scroll = new JScrollPane(list);

        scroll.setBorder(null);

        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroll.getVerticalScrollBar().setUnitIncrement(56);

        add(scroll, BorderLayout.CENTER);

    }



    void bind(ImageEditorCanvas canvas, Runnable onJump) {

        this.canvas = canvas;

        this.onJump = onJump;

        refresh();

    }



    void refresh() {

        model.clear();

        if (canvas == null) {

            return;

        }

        for (ImageEditorHistory.Entry entry : canvas.getHistory().getEntries()) {

            model.addElement(entry);

        }

        refreshSelection();

    }



    void refreshSelection() {

        if (canvas == null) {

            return;

        }

        int idx = canvas.getHistory().getCurrentIndex();

        if (idx >= 0 && idx < model.size()) {

            list.setSelectedIndex(idx);

            list.ensureIndexIsVisible(idx);

        }

    }



    private static final class HistoryCellRenderer extends DefaultListCellRenderer {

        @Override

        public Component getListCellRendererComponent(JList<?> list, Object value, int index,

                                                      boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            label.setBorder(new EmptyBorder(4, 8, 4, 8));

            label.setVerticalAlignment(SwingConstants.CENTER);

            if (value instanceof ImageEditorHistory.Entry entry) {

                label.setIcon(entry.thumbnail());

                label.setText("  " + entry.label());

            }

            if (isSelected) {

                label.setBackground(new Color(0x0078D7));

                label.setForeground(Color.WHITE);

            }

            return label;

        }

    }

}


