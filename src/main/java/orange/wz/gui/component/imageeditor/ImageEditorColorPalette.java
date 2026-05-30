package orange.wz.gui.component.imageeditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class ImageEditorColorPalette extends JPanel {

    private static final int SWATCH = 18;
    private static final int COLS = 11;
    private static final int GAP = 3;

    private final ImageEditorPrefs prefs;
    private Runnable colorChangedCallback;
    private int selectedIndex = -1;

    ImageEditorColorPalette(ImageEditorPrefs prefs) {
        this.prefs = prefs;
        setLayout(new GridLayout(0, COLS, GAP, GAP));
        setBorder(new EmptyBorder(4, 0, 2, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        rebuild();
    }

    void setColorChangedCallback(Runnable callback) {
        this.colorChangedCallback = callback;
    }

    void setSelectedColor(Color color) {
        for (int i = 0; i < prefs.palette.size(); i++) {
            if (prefs.palette.get(i).equals(color)) {
                selectIndex(i);
                return;
            }
        }
    }

    void rebuild() {
        removeAll();
        selectedIndex = prefs.selectedPaletteIndex;
        for (int i = 0; i < prefs.palette.size(); i++) {
            final int idx = i;
            Color c = prefs.palette.get(i);
            JPanel swatch = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(c);
                    g.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
                    if (idx == selectedIndex) {
                        g.setColor(Color.WHITE);
                        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                        g.setColor(Color.BLACK);
                        g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    } else {
                        g.setColor(Color.GRAY);
                        g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                }
            };
            swatch.setPreferredSize(new Dimension(SWATCH, SWATCH));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText(ImageEditorPrefs.colorToHex(c));
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        Color picked = JColorChooser.showDialog(swatch, "修改颜色", c);
                        if (picked != null) {
                            prefs.palette.set(idx, picked);
                            rebuild();
                            notifyColor(picked);
                            prefs.selectedPaletteIndex = idx;
                        }
                    } else {
                        selectIndex(idx);
                        notifyColor(prefs.palette.get(idx));
                    }
                }
            });
            add(swatch);
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int count = Math.max(1, prefs.palette.size());
        int rows = (count + COLS - 1) / COLS;
        Insets in = getInsets();
        int w = COLS * SWATCH + (COLS - 1) * GAP + in.left + in.right;
        int h = rows * SWATCH + (rows - 1) * GAP + in.top + in.bottom;
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private void selectIndex(int idx) {
        selectedIndex = idx;
        prefs.selectedPaletteIndex = idx;
        rebuild();
    }

    private void notifyColor(Color c) {
        prefs.foregroundColor = c;
        if (colorChangedCallback != null) {
            colorChangedCallback.run();
        }
    }
}
