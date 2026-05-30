package orange.wz.gui.component.imageeditor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

final class ImageEditorHistoryWindow extends JDialog {



    private static final int WIDTH = 220;



    private final ImageEditorHistoryPanel panel = new ImageEditorHistoryPanel();

    private ImageEditorCanvas boundCanvas;

    private JFrame ownerFrame;

    private final ComponentAdapter ownerSyncListener = new ComponentAdapter() {

        @Override

        public void componentMoved(ComponentEvent e) {

            syncToOwner();

        }



        @Override

        public void componentResized(ComponentEvent e) {

            syncToOwner();

        }

    };



    ImageEditorHistoryWindow(JFrame owner) {

        super(owner);

        this.ownerFrame = owner;

        setUndecorated(true);

        setDefaultCloseOperation(HIDE_ON_CLOSE);

        setLayout(new BorderLayout());

        panel.setBorder(new TitledBorder("历史记录"));

        add(panel, BorderLayout.CENTER);

        setSize(WIDTH, 400);

        setResizable(false);

        attachOwnerListener(owner);

    }



    private void attachOwnerListener(JFrame owner) {

        if (owner == null) {

            return;

        }

        owner.removeComponentListener(ownerSyncListener);

        owner.addComponentListener(ownerSyncListener);

    }



    void bindCanvas(ImageEditorCanvas canvas, Runnable onJump) {

        boundCanvas = canvas;

        panel.bind(canvas, onJump);

    }



    ImageEditorCanvas getBoundCanvas() {

        return boundCanvas;

    }



    void refresh() {

        panel.refresh();

    }



    void refreshSelection() {

        panel.refreshSelection();

    }



    void syncToOwner() {

        if (ownerFrame == null || !isVisible()) {

            return;

        }

        Point loc = ownerFrame.getLocationOnScreen();

        Dimension size = ownerFrame.getSize();

        setBounds(loc.x + size.width, loc.y, WIDTH, size.height);

    }



    void toggle(JFrame owner) {

        this.ownerFrame = owner;

        attachOwnerListener(owner);

        if (isVisible()) {

            setVisible(false);

        } else {

            syncToOwner();

            refresh();

            setVisible(true);

            toFront();

        }

    }



    boolean isShowingWindow() {

        return isVisible();

    }



    void disposeWindow() {

        if (ownerFrame != null) {

            ownerFrame.removeComponentListener(ownerSyncListener);

        }

        setVisible(false);

        dispose();

    }

}


