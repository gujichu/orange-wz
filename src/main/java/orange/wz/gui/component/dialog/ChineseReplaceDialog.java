package orange.wz.gui.component.dialog;

import orange.wz.gui.Icons;
import orange.wz.gui.utils.ChineseReplaceEntry;
import orange.wz.gui.utils.ChineseReplaceEntry.Status;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public final class ChineseReplaceDialog extends JDialog {

    private final List<ChineseReplaceEntry> entries;
    private final ReplaceTableModel tableModel;
    private final JTable table;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JButton startBtn;
    private final JButton stopBtn;
    private final JButton finishBtn;
    private final JButton closeBtn;

    private SwingWorker<Void, Integer> replaceWorker;
    private volatile boolean stopRequested;
    private boolean finished;

    private ChineseReplaceDialog(Window owner, List<ChineseReplaceEntry> entries, int missingCount) {
        super(owner, "汉化替换", ModalityType.APPLICATION_MODAL);
        this.entries = entries;

        setSize(960, 560);
        setMinimumSize(new Dimension(720, 420));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel(buildSummaryText(missingCount));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        add(statusLabel, BorderLayout.NORTH);

        tableModel = new ReplaceTableModel(entries);
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(Math.max(table.getRowHeight(), 24));

        TableColumn statusCol = table.getColumnModel().getColumn(0);
        statusCol.setPreferredWidth(36);
        statusCol.setMaxWidth(44);
        statusCol.setCellRenderer(new StatusIconRenderer());

        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        for (int col = 1; col <= 3; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
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
            });
        }
        table.getColumnModel().getColumn(4).setPreferredWidth(72);
        table.getColumnModel().getColumn(4).setCellRenderer(new OperationRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 1) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || col != 4) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(row);
                rollbackRow(modelRow);
            }
        });

        add(new JScrollPane(table,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        progressBar = new JProgressBar(0, Math.max(1, entries.size()));
        progressBar.setStringPainted(true);
        progressBar.setString("0 / " + entries.size());

        startBtn = new JButton("开始替换");
        stopBtn = new JButton("停止替换");
        finishBtn = new JButton("结束替换");
        closeBtn = new JButton("关闭");

        stopBtn.setEnabled(false);
        finishBtn.setEnabled(true);

        startBtn.addActionListener(e -> startReplace());
        stopBtn.addActionListener(e -> requestStop());
        finishBtn.addActionListener(e -> finishReplace());
        closeBtn.addActionListener(e -> dispose());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                requestStop();
                if (replaceWorker != null) {
                    replaceWorker.cancel(true);
                }
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(startBtn);
        buttons.add(stopBtn);
        buttons.add(finishBtn);
        buttons.add(closeBtn);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(progressBar, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        updateProgress();
    }

    private String buildSummaryText(int missingCount) {
        String base = String.format("共 %d 项待替换 String 属性（仅路径匹配的节点）", entries.size());
        if (missingCount > 0) {
            return base + String.format("；另有 %d 个选中根节点未找到对照路径", missingCount);
        }
        return base;
    }

    private void startReplace() {
        if (replaceWorker != null || finished) {
            return;
        }
        stopRequested = false;
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        finishBtn.setEnabled(false);

        replaceWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < entries.size(); i++) {
                    if (stopRequested) {
                        break;
                    }
                    ChineseReplaceEntry entry = entries.get(i);
                    if (entry.isPending()) {
                        publish(i);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Integer> rows) {
                for (int row : rows) {
                    entries.get(row).apply();
                    tableModel.fireTableRowsUpdated(row, row);
                }
                updateProgress();
            }

            @Override
            protected void done() {
                replaceWorker = null;
                stopBtn.setEnabled(false);
                if (!finished) {
                    startBtn.setEnabled(hasPending());
                    finishBtn.setEnabled(true);
                }
                updateProgress();
            }
        };
        replaceWorker.execute();
    }

    private void requestStop() {
        stopRequested = true;
        stopBtn.setEnabled(false);
    }

    private void finishReplace() {
        requestStop();
        finished = true;
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        finishBtn.setEnabled(false);
        statusLabel.setText(statusLabel.getText() + "（已结束，可回滚单项或关闭窗口）");
    }

    private void rollbackRow(int modelRow) {
        if (modelRow < 0 || modelRow >= entries.size()) {
            return;
        }
        ChineseReplaceEntry entry = entries.get(modelRow);
        if (!entry.canRollback()) {
            return;
        }
        entry.rollback();
        tableModel.fireTableRowsUpdated(modelRow, modelRow);
        updateProgress();
        if (!finished && replaceWorker == null) {
            startBtn.setEnabled(hasPending());
        }
    }

    private boolean hasPending() {
        for (ChineseReplaceEntry entry : entries) {
            if (entry.isPending()) {
                return true;
            }
        }
        return false;
    }

    private void updateProgress() {
        int applied = 0;
        int pending = 0;
        int rolled = 0;
        for (ChineseReplaceEntry entry : entries) {
            switch (entry.getStatus()) {
                case APPLIED -> applied++;
                case PENDING -> pending++;
                case ROLLED_BACK -> rolled++;
            }
        }
        int done = applied + rolled;
        progressBar.setMaximum(Math.max(1, entries.size()));
        progressBar.setValue(Math.min(done, entries.size()));
        progressBar.setString(String.format("%d / %d（待替换 %d，已替换 %d，已回滚 %d）",
                done, entries.size(), pending, applied, rolled));
    }

    public static void show(Component parent, List<ChineseReplaceEntry> entries, int missingCount) {
        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        ChineseReplaceDialog dialog = new ChineseReplaceDialog(owner, entries, missingCount);
        dialog.setVisible(true);
    }

    private static final class ReplaceTableModel extends AbstractTableModel {
        private final List<ChineseReplaceEntry> entries;
        private final String[] columns = {"状态", "节点", "修改前", "修改后", "操作"};

        ReplaceTableModel(List<ChineseReplaceEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ChineseReplaceEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getStatus();
                case 1 -> entry.getPath();
                case 2 -> entry.getBefore();
                case 3 -> entry.getAfter();
                case 4 -> entry.operationLabel();
                default -> "";
            };
        }
    }

    private static final class StatusIconRenderer extends DefaultTableCellRenderer {
        StatusIconRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            setText("");
            if (value instanceof Status status) {
                setIcon(switch (status) {
                    case PENDING -> Icons.AiOutlineReloadIcon;
                    case APPLIED -> Icons.AiOutlineSaveIcon;
                    case ROLLED_BACK -> Icons.AiOutlineCloseIcon;
                });
                setToolTipText(status.label());
            } else {
                setIcon(null);
            }
            return this;
        }
    }

    private static final class OperationRenderer extends DefaultTableCellRenderer {
        OperationRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value != null ? value.toString() : "";
            setText(text);
            if ("回滚".equals(text)) {
                setForeground(isSelected ? table.getSelectionForeground() : new Color(0x1565C0));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("点击回滚该项");
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                setCursor(Cursor.getDefaultCursor());
                setToolTipText(null);
            }
            return this;
        }
    }
}
