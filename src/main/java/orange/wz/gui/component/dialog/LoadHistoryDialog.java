package orange.wz.gui.component.dialog;

import orange.wz.gui.Icons;
import orange.wz.gui.utils.JMessageUtil;
import orange.wz.gui.utils.LoadHistoryStorage;
import orange.wz.gui.utils.LoadHistoryStorage.LoadHistoryEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class LoadHistoryDialog extends JDialog {

    private static final int COL_FOLDER = 0;
    private static final int COL_PATH = 1;
    private static final int COL_TIME = 2;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JTable table;
    private final List<LoadHistoryEntry> entries;
    private Consumer<List<File>> onLoad = files -> {
    };

    public LoadHistoryDialog(Window owner) {
        super(owner, "历史加载", ModalityType.APPLICATION_MODAL);
        entries = LoadHistoryStorage.getInstance().listByTimeDesc();

        setSize(820, 420);
        setMinimumSize(new Dimension(520, 320));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton loadBtn = new JButton("加载");
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());

        if (entries.isEmpty()) {
            table = null;
            JLabel emptyLabel = new JLabel("暂无加载记录", SwingConstants.CENTER);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.PLAIN, 16f));
            emptyLabel.setForeground(Color.GRAY);
            add(emptyLabel, BorderLayout.CENTER);
            loadBtn.setEnabled(false);
        } else {
            String[] columns = {"", "文件路径", "加载时间"};
            Object[][] data = new Object[entries.size()][3];
            for (int i = 0; i < entries.size(); i++) {
                LoadHistoryEntry entry = entries.get(i);
                data[i][COL_FOLDER] = entry;
                data[i][COL_PATH] = entry.path();
                data[i][COL_TIME] = TIME_FORMAT.format(Instant.ofEpochMilli(entry.loadTime()));
            }

            DefaultTableModel model = new DefaultTableModel(data, columns) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setRowSelectionAllowed(true);
            table.setColumnSelectionAllowed(false);
            table.setAutoCreateRowSorter(false);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            table.setRowHeight(Math.max(table.getRowHeight(), 22));
            table.setToolTipText("双击记录可加载；按住 Ctrl 或 Shift 可多选");

            TableColumn folderCol = table.getColumnModel().getColumn(COL_FOLDER);
            folderCol.setPreferredWidth(32);
            folderCol.setMaxWidth(40);
            folderCol.setMinWidth(32);
            folderCol.setHeaderValue("");
            folderCol.setCellRenderer(new FolderIconRenderer());

            table.getColumnModel().getColumn(COL_PATH).setPreferredWidth(480);
            table.getColumnModel().getColumn(COL_PATH).setCellRenderer(new PathCellRenderer());
            table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(180);

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row < 0) {
                        return;
                    }
                    int col = table.columnAtPoint(e.getPoint());
                    if (col == COL_FOLDER && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                        openContainingFolder(entryAt(row));
                        return;
                    }
                    if (col != COL_FOLDER && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        loadRows(new int[]{row});
                    }
                }
            });

            add(new JScrollPane(table,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

            loadBtn.addActionListener(e -> loadSelected());
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(loadBtn);
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(loadBtn);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private LoadHistoryEntry entryAt(int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        return entries.get(modelRow);
    }

    private void openContainingFolder(LoadHistoryEntry entry) {
        File file = new File(entry.path());
        File dir = file.isDirectory() ? file : file.getParentFile();
        if (dir == null || !dir.isDirectory()) {
            JMessageUtil.warn(this, "历史加载", "文件夹不存在或已被移动");
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            JMessageUtil.warn(this, "历史加载", "当前系统不支持打开文件夹");
            return;
        }
        try {
            Desktop.getDesktop().open(dir);
        } catch (IOException ex) {
            JMessageUtil.error(this, "历史加载", "打开文件夹失败: " + dir.getAbsolutePath());
        }
    }

    private void loadSelected() {
        if (table == null || entries.isEmpty()) {
            JMessageUtil.warn(this, "历史加载", "暂无加载记录");
            return;
        }
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            JMessageUtil.warn(this, "历史加载", "请选择需要加载的记录");
            return;
        }
        loadRows(rows);
    }

    private void loadRows(int[] viewRows) {
        List<File> files = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (int viewRow : viewRows) {
            LoadHistoryEntry entry = entryAt(viewRow);
            File file = new File(entry.path());
            if (file.exists()) {
                files.add(file);
            } else {
                missing.add(entry.path());
            }
        }

        if (files.isEmpty()) {
            JMessageUtil.warn(this, "历史加载", "所选文件不存在或已被移动");
            return;
        }
        if (!missing.isEmpty()) {
            JMessageUtil.warn(this, "历史加载",
                    "部分记录已跳过（文件不存在）：" + String.join("、", missing));
        }

        onLoad.accept(files);
        dispose();
    }

    public LoadHistoryDialog onLoad(Consumer<List<File>> onLoad) {
        this.onLoad = onLoad != null ? onLoad : files -> {
        };
        return this;
    }

    public static void show(Window owner, Consumer<List<File>> onLoad) {
        LoadHistoryDialog dialog = new LoadHistoryDialog(owner);
        dialog.onLoad(onLoad);
        dialog.setVisible(true);
    }

    private static final class PathCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value != null ? value.toString() : "";
            setText(text);
            setToolTipText(text);
            return this;
        }
    }

    private static final class FolderIconRenderer extends DefaultTableCellRenderer {
        FolderIconRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setIcon(Icons.FcFolderIcon);
            setText("");
            setToolTipText("打开所在文件夹");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            setIcon(Icons.FcFolderIcon);
            setText("");
            setToolTipText("打开所在文件夹");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return this;
        }
    }
}
