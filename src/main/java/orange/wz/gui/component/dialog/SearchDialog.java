package orange.wz.gui.component.dialog;

import orange.wz.gui.component.form.data.SearchFormData;
import orange.wz.gui.component.form.data.SearchResult;
import orange.wz.gui.component.panel.EditPane;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SearchDialog extends JDialog {
    public interface SearchHandler {
        void search(SearchFormData option);
    }

    private final EditPane editPane;
    private final SearchHandler searchHandler;
    private final JTextField searchField = new JTextField(28);
    private final JCheckBox checkNameMod = new JCheckBox("名称");
    private final JCheckBox checkValueMod = new JCheckBox("String值");
    private final JCheckBox checkEqualMod = new JCheckBox("完整匹配");
    private final JCheckBox checkLowMod = new JCheckBox("忽略大小写");
    private final JRadioButton checkSelectedMod = new JRadioButton("搜选中");
    private final JRadioButton checkGlobalMod = new JRadioButton("搜全局");
    private final JCheckBox checkParseImgMod = new JCheckBox("自动解析IMG");
    private final JButton searchButton = new JButton("搜索");
    private final JButton cancelButton = new JButton("取消");
    private final JLabel statusLabel = new JLabel("输入关键字后点击搜索");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final List<SearchResult> items = new ArrayList<>();
    private String filterText = "";

    public SearchDialog(Frame owner, EditPane editPane, SearchHandler searchHandler, Runnable cancelHandler) {
        super(owner, "搜索", false);
        this.editPane = editPane;
        this.searchHandler = searchHandler;

        setSize(760, 480);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        checkNameMod.setSelected(true);
        checkParseImgMod.setSelected(true);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(checkSelectedMod);
        scopeGroup.add(checkGlobalMod);
        checkSelectedMod.setSelected(true);

        tableModel = new DefaultTableModel(new Object[]{"节点", "String值", "路径"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(0).setMaxWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setMaxWidth(320);
        table.setAutoCreateRowSorter(false);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        installHighlightRenderer();
        installResultNavigation();

        add(createOptionPanel(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createStatusPanel(cancelHandler), BorderLayout.SOUTH);
        installShortcuts(cancelHandler);
    }

    public void open(boolean forceGlobal) {
        if (forceGlobal) {
            checkGlobalMod.setSelected(true);
        }
        setVisible(true);
        toFront();
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    public void setSearching(boolean searching) {
        searchButton.setEnabled(!searching);
        cancelButton.setEnabled(searching);
        searchField.setEnabled(!searching);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setResults(List<SearchResult> results, String search) {
        items.clear();
        items.addAll(results);
        filterText = search == null ? "" : search.trim();
        tableModel.setRowCount(0);
        for (SearchResult result : items) {
            tableModel.addRow(new Object[]{result.name(), result.value(), result.getPathString()});
        }
        table.repaint();
    }

    private JPanel createOptionPanel() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel query = new JPanel(new BorderLayout(6, 0));
        query.add(new JLabel("目标"), BorderLayout.WEST);
        query.add(searchField, BorderLayout.CENTER);
        query.add(searchButton, BorderLayout.EAST);
        root.add(query, BorderLayout.NORTH);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(new JLabel("搜索"));
        options.add(checkNameMod);
        options.add(checkValueMod);
        options.add(checkEqualMod);
        options.add(checkLowMod);
        options.add(Box.createHorizontalStrut(12));
        options.add(new JLabel("范围"));
        options.add(checkSelectedMod);
        options.add(checkGlobalMod);
        options.add(Box.createHorizontalStrut(12));
        options.add(checkParseImgMod);
        root.add(options, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> submitSearch());
        searchField.addActionListener(e -> submitSearch());
        return root;
    }

    private JPanel createStatusPanel(Runnable cancelHandler) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        panel.add(statusLabel, BorderLayout.CENTER);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelHandler.run());
        panel.add(cancelButton, BorderLayout.EAST);
        return panel;
    }

    private void submitSearch() {
        String search = searchField.getText();
        if (search == null || search.isBlank()) {
            setStatus("请输入搜索内容");
            return;
        }
        if (!checkNameMod.isSelected() && !checkValueMod.isSelected()) {
            setStatus("至少选择名称或 String值之一");
            return;
        }
        searchHandler.search(new SearchFormData(
                search,
                checkNameMod.isSelected(),
                checkValueMod.isSelected(),
                checkEqualMod.isSelected(),
                checkLowMod.isSelected(),
                checkParseImgMod.isSelected(),
                checkGlobalMod.isSelected()
        ));
    }

    private void installResultNavigation() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < items.size()) {
                    editPane.focusNodeByPath(items.get(modelRow).path());
                }
            }
        });
    }

    private void installHighlightRenderer() {
        TableCellRenderer highlightRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String text = value != null ? value.toString() : "";
                setToolTipText(text);
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                String displayText = ellipsis(text, table, column);
                if (!filterText.isBlank()) {
                    String regex = "(?i)" + Pattern.quote(filterText);
                    displayText = displayText.replaceAll(regex, "<span style='background:yellow;color:" + (isSelected ? "black" : "red") + "'>$0</span>");
                    setText("<html>" + displayText + "</html>");
                } else {
                    setText(displayText);
                }
                return this;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(highlightRenderer);
        }
    }

    private void installShortcuts(Runnable cancelHandler) {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSearch");
        am.put("cancelSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelHandler.run();
            }
        });
    }

    private String ellipsis(String text, JTable table, int column) {
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int colWidth = table.getColumnModel().getColumn(column).getWidth() - 6;
        if (fm.stringWidth(text) <= colWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int len = text.length();
        while (len > 0) {
            String s = text.substring(0, len);
            if (fm.stringWidth(s) + ellipsisWidth <= colWidth) {
                return s + ellipsis;
            }
            len--;
        }
        return ellipsis;
    }
}