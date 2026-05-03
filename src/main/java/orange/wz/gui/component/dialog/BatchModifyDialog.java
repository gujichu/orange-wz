package orange.wz.gui.component.dialog;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.filter.DecimalFilter;
import orange.wz.gui.filter.IntegerFilter;
import orange.wz.gui.utils.JMessageUtil;
import orange.wz.gui.utils.SkillPropertyInfoJson;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.*;
import orange.wz.provider.tools.WzType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 在列表类父节点下，按同名子属性批量同步数值。
 */
@Slf4j
public final class BatchModifyDialog extends JDialog {
    private static final Color SUCCESS_PINK = new Color(255, 182, 193);

    /** 左:中:右 内容区宽度比例（与水平边距 20 一起构成初始总宽约 680） */
    private static final int RATIO_LEFT = 160;
    private static final int RATIO_MIDDLE = 280;
    private static final int RATIO_RIGHT = 220;
    private static final int RATIO_SUM = RATIO_LEFT + RATIO_MIDDLE + RATIO_RIGHT;

    private final EditPane editPane;
    private final WzObject scopeRoot;

    private final JSplitPane outerSplit;
    private final JSplitPane innerSplit;
    private final JPanel splitHost;

    private final DefaultListModel<String> leftModel = new DefaultListModel<>();
    private final JList<String> leftList = new JList<>(leftModel);

    private final List<MiddleRow> middleRows = new ArrayList<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"勾选", "属性位置"}, 0) {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }
    };
    private final JTable middleTable = new JTable(tableModel);

    private final JPanel editorHost = new JPanel(new BorderLayout());
    private final JPanel editorCenterPanel = new JPanel(new BorderLayout());
    private final JPanel meaningPanel = new JPanel(new BorderLayout());
    private final JTextArea propertyMeaningArea = new JTextArea(3, 18);
    private ValueAdapter currentAdapter = ValueAdapter.NONE;

    private String selectedPropertyName;

    public BatchModifyDialog(EditPane editPane, WzObject scopeRoot) {
        super(SwingUtilities.getWindowAncestor(editPane), "批量修改", ModalityType.APPLICATION_MODAL);
        this.editPane = editPane;
        this.scopeRoot = scopeRoot;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(680, 640);
        setLocationRelativeTo(editPane);

        leftList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftList.setBorder(new TitledBorder("可修改属性"));
        leftList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onLeftSelection();
            }
        });

        middleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        middleTable.getColumnModel().getColumn(0).setMaxWidth(48);
        middleTable.getColumnModel().getColumn(1).setCellRenderer(new PathCellRenderer());
        middleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onMiddleSelection();
            }
        });

        JPanel rightPanel = new JPanel(new BorderLayout());
        editorHost.setBorder(new TitledBorder("属性值"));
        propertyMeaningArea.setEditable(false);
        propertyMeaningArea.setOpaque(false);
        propertyMeaningArea.setLineWrap(true);
        propertyMeaningArea.setWrapStyleWord(true);
        JScrollPane meaningScroll = new JScrollPane(propertyMeaningArea);
        meaningScroll.setBorder(BorderFactory.createEmptyBorder());
        meaningPanel.setBorder(new TitledBorder("含义"));
        meaningPanel.add(meaningScroll, BorderLayout.CENTER);
        meaningPanel.setVisible(false);
        editorHost.add(editorCenterPanel, BorderLayout.CENTER);
        editorHost.add(meaningPanel, BorderLayout.SOUTH);
        rightPanel.add(editorHost, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton syncBtn = new JButton("批量同步");
        JButton backBtn = new JButton("返回");
        syncBtn.addActionListener(e -> onBatchSync());
        backBtn.addActionListener(e -> dispose());
        btnRow.add(syncBtn);
        btnRow.add(backBtn);
        rightPanel.add(btnRow, BorderLayout.SOUTH);

        innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(middleTable), rightPanel);
        innerSplit.setContinuousLayout(true);
        innerSplit.setResizeWeight((double) RATIO_MIDDLE / (RATIO_MIDDLE + RATIO_RIGHT));

        outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(leftList), innerSplit);
        outerSplit.setContinuousLayout(true);
        outerSplit.setResizeWeight((double) RATIO_LEFT / RATIO_SUM);

        splitHost = new JPanel(new BorderLayout());
        splitHost.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        splitHost.add(outerSplit, BorderLayout.CENTER);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitHost, BorderLayout.CENTER);

        splitHost.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                syncSplitDividersToRatio();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(BatchModifyDialog.this::syncSplitDividersToRatio);
            }
        });

        fillLeftPropertyNames();
        if (leftModel.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                JMessageUtil.warn("当前节点下没有可批量修改的子属性（需为列表子节点下的数值类属性）");
                dispose();
            });
        } else {
            leftList.setSelectedIndex(0);
            onLeftSelection();
        }
    }

    /**
     * 按 160:280:220 比例设置左右两条分割条位置（基于当前 {@link #outerSplit} 宽度，不固定像素）。
     */
    private void syncSplitDividersToRatio() {
        int total = outerSplit.getWidth();
        if (total <= 10) {
            return;
        }
        int outerLoc = (int) Math.round(total * (double) RATIO_LEFT / RATIO_SUM);
        outerLoc = Math.max(outerSplit.getMinimumDividerLocation(), Math.min(outerSplit.getMaximumDividerLocation(), outerLoc));
        outerSplit.setDividerLocation(outerLoc);

        splitHost.validate();
        int innerTotal = innerSplit.getWidth();
        if (innerTotal <= 10) {
            return;
        }
        int innerLoc = (int) Math.round(innerTotal * (double) RATIO_MIDDLE / (RATIO_MIDDLE + RATIO_RIGHT));
        innerLoc = Math.max(innerSplit.getMinimumDividerLocation(), Math.min(innerSplit.getMaximumDividerLocation(), innerLoc));
        innerSplit.setDividerLocation(innerLoc);
    }

    private List<WzImageProperty> directChildren() {
        if (scopeRoot instanceof WzImage image) {
            List<WzImageProperty> ch = image.getChildren();
            return ch != null ? ch : List.of();
        }
        if (scopeRoot instanceof WzImageProperty prop && prop.isListProperty()) {
            List<WzImageProperty> ch = prop.getChildren();
            return ch != null ? ch : List.of();
        }
        return List.of();
    }

    private static boolean isBatchableValueType(WzType t) {
        return switch (t) {
            case INT_PROPERTY, SHORT_PROPERTY, LONG_PROPERTY, FLOAT_PROPERTY, DOUBLE_PROPERTY,
                 STRING_PROPERTY, VECTOR_PROPERTY, LUA_PROPERTY -> true;
            default -> false;
        };
    }

    private void fillLeftPropertyNames() {
        Set<String> names = new TreeSet<>();
        for (WzImageProperty rowParent : directChildren()) {
            if (!rowParent.isListProperty()) continue;
            List<WzImageProperty> gs = rowParent.getChildren();
            if (gs == null) continue;
            for (WzImageProperty g : gs) {
                if (isBatchableValueType(g.getType())) {
                    names.add(g.getName());
                }
            }
        }
        for (String n : names) {
            leftModel.addElement(n);
        }
    }

    private void onLeftSelection() {
        String name = leftList.getSelectedValue();
        if (name == null) return;
        selectedPropertyName = name;
        middleRows.clear();
        tableModel.setRowCount(0);
        for (WzImageProperty rowParent : directChildren()) {
            if (!rowParent.isListProperty()) continue;
            WzImageProperty prop = rowParent.getChild(name);
            if (prop != null && isBatchableValueType(prop.getType())) {
                String label = rowParent.getName() + "/" + name;
                middleRows.add(new MiddleRow(label, prop));
                tableModel.addRow(new Object[]{Boolean.TRUE, label});
            }
        }
        currentAdapter = ValueAdapter.NONE;
        editorCenterPanel.removeAll();
        setPropertyMeaning(null);
        editorCenterPanel.revalidate();
        editorCenterPanel.repaint();
        if (!middleRows.isEmpty()) {
            middleTable.setRowSelectionInterval(0, 0);
            onMiddleSelection();
        }
    }

    private void onMiddleSelection() {
        int row = middleTable.getSelectedRow();
        if (row < 0 || row >= middleRows.size()) {
            currentAdapter = ValueAdapter.NONE;
            editorCenterPanel.removeAll();
            setPropertyMeaning(null);
            editorCenterPanel.revalidate();
            editorCenterPanel.repaint();
            return;
        }
        WzImageProperty prop = middleRows.get(row).property;
        currentAdapter = ValueAdapter.forProperty(prop);
        editorCenterPanel.removeAll();
        editorCenterPanel.add(currentAdapter.getComponent(), BorderLayout.CENTER);
        currentAdapter.loadFrom(prop);
        setPropertyMeaning(prop.getName());
        editorCenterPanel.revalidate();
        editorCenterPanel.repaint();
    }

    private void setPropertyMeaning(String propertyName) {
        String desc = SkillPropertyInfoJson.lookup(propertyName);
        if (desc != null && !desc.isEmpty()) {
            propertyMeaningArea.setText(desc);
            meaningPanel.setVisible(true);
        } else {
            propertyMeaningArea.setText("");
            meaningPanel.setVisible(false);
        }
    }

    private int countChecked() {
        int n = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object v = tableModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(v)) n++;
        }
        return n;
    }

    private void onBatchSync() {
        if (selectedPropertyName == null || middleRows.isEmpty()) {
            JMessageUtil.warn("请先选择左侧属性");
            return;
        }
        int n = countChecked();
        if (n == 0) {
            JMessageUtil.warn("请至少勾选一项要同步的节点");
            return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "是否要对 " + n + " 个节点的「" + selectedPropertyName + "」执行批量修改？",
                "确认批量修改",
                JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;

        List<WzImageProperty> refreshed = new ArrayList<>();
        int ok = 0, fail = 0;
        for (int i = 0; i < middleRows.size(); i++) {
            if (!Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) continue;
            MiddleRow mr = middleRows.get(i);
            String err = currentAdapter.copyValuesTo(mr.property);
            if (err != null) {
                log.warn("批量修改失败 {} : {}", mr.property.getPath(), err);
                fail++;
                continue;
            }
            mr.syncSuccess = true;
            ok++;
            refreshed.add(mr.property);
        }
        middleTable.repaint();
        editPane.refreshAfterBatchPropertyEdit(refreshed);

        String msg = "修改成功 " + ok + " 个节点";
        if (fail > 0) {
            msg += "，失败 " + fail + " 个（详情见日志）";
        }
        JMessageUtil.info(msg);
    }

    private final class PathCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (column == 1 && row >= 0 && row < middleRows.size() && middleRows.get(row).syncSuccess) {
                c.setBackground(SUCCESS_PINK);
            } else if (!isSelected) {
                c.setBackground(table.getBackground());
            }
            return c;
        }
    }

    private static final class MiddleRow {
        final String label;
        final WzImageProperty property;
        boolean syncSuccess;

        MiddleRow(String label, WzImageProperty property) {
            this.label = label;
            this.property = property;
        }
    }

    private interface ValueAdapter {
        ValueAdapter NONE = new ValueAdapter() {
            @Override
            public JComponent getComponent() {
                return new JLabel("请选择中间列表中的一项", SwingConstants.CENTER);
            }

            @Override
            public void loadFrom(WzImageProperty prop) {
            }

            @Override
            public String copyValuesTo(WzImageProperty target) {
                return "未选择编辑器";
            }
        };

        JComponent getComponent();

        void loadFrom(WzImageProperty prop);

        /** null 表示成功 */
        String copyValuesTo(WzImageProperty target);

        static ValueAdapter forProperty(WzImageProperty prop) {
            return switch (prop.getType()) {
                case VECTOR_PROPERTY -> new VectorAdapter();
                case INT_PROPERTY -> new IntAdapter();
                case SHORT_PROPERTY -> new ShortAdapter();
                case LONG_PROPERTY -> new LongAdapter();
                case FLOAT_PROPERTY -> new FloatAdapter();
                case DOUBLE_PROPERTY -> new DoubleAdapter();
                case STRING_PROPERTY -> new StringAdapter();
                case LUA_PROPERTY -> new LuaAdapter();
                default -> new UnsupportedAdapter(prop.getType().name());
            };
        }
    }

    private static final class UnsupportedAdapter implements ValueAdapter {
        private final String typeName;

        UnsupportedAdapter(String typeName) {
            this.typeName = typeName;
        }

        @Override
        public JComponent getComponent() {
            return new JLabel("不支持批量修改类型: " + typeName, SwingConstants.CENTER);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            return "不支持的类型";
        }
    }

    private abstract static class FieldAdapter implements ValueAdapter {
        protected final JPanel panel = new JPanel(new GridBagLayout());
        protected int row;

        FieldAdapter() {
            panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        }

        protected void addRow(String label, JComponent field) {
            GridBagConstraints lg = new GridBagConstraints();
            lg.gridx = 0;
            lg.gridy = row;
            lg.anchor = GridBagConstraints.WEST;
            lg.insets = new Insets(4, 4, 4, 4);
            panel.add(new JLabel(label), lg);
            GridBagConstraints fg = new GridBagConstraints();
            fg.gridx = 1;
            fg.gridy = row;
            fg.weightx = 1;
            fg.fill = GridBagConstraints.HORIZONTAL;
            fg.insets = new Insets(4, 4, 4, 4);
            panel.add(field, fg);
            row++;
        }

        @Override
        public JComponent getComponent() {
            return panel;
        }

        protected void markChanged(WzImageProperty target) {
            target.setTempChanged(true);
            if (target.getWzImage() != null) {
                target.getWzImage().setChanged(true);
                target.getWzImage().setTempChanged(true);
            }
        }
    }

    private static final class VectorAdapter extends FieldAdapter {
        private final JTextField xField = new JTextField(16);
        private final JTextField yField = new JTextField(16);

        VectorAdapter() {
            ((AbstractDocument) xField.getDocument()).setDocumentFilter(new IntegerFilter());
            ((AbstractDocument) yField.getDocument()).setDocumentFilter(new IntegerFilter());
            addRow("X:", xField);
            addRow("Y:", yField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            WzVectorProperty v = (WzVectorProperty) prop;
            xField.setText(String.valueOf(v.getX()));
            yField.setText(String.valueOf(v.getY()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzVectorProperty v)) return "类型不是向量";
            int x, y;
            try {
                x = Integer.parseInt(xField.getText().trim());
                y = Integer.parseInt(yField.getText().trim());
            } catch (NumberFormatException e) {
                return "X/Y 不是有效整数";
            }
            v.setX(x);
            v.setY(y);
            markChanged(v);
            return null;
        }
    }

    private static final class IntAdapter extends FieldAdapter {
        private final JTextField valueField = new JTextField(20);

        IntAdapter() {
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new IntegerFilter());
            addRow("值:", valueField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            valueField.setText(String.valueOf(((WzIntProperty) prop).getValue()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzIntProperty p)) return "类型不是 Int";
            try {
                p.setValue(Integer.parseInt(valueField.getText().trim()));
            } catch (NumberFormatException e) {
                return "值不是有效整数";
            }
            markChanged(p);
            return null;
        }
    }

    private static final class ShortAdapter extends FieldAdapter {
        private final JTextField valueField = new JTextField(20);

        ShortAdapter() {
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new IntegerFilter());
            addRow("值:", valueField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            valueField.setText(String.valueOf(((WzShortProperty) prop).getValue()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzShortProperty p)) return "类型不是 Short";
            try {
                p.setValue(Short.parseShort(valueField.getText().trim()));
            } catch (NumberFormatException e) {
                return "值不是有效短整数";
            }
            markChanged(p);
            return null;
        }
    }

    private static final class LongAdapter extends FieldAdapter {
        private final JTextField valueField = new JTextField(24);

        LongAdapter() {
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new IntegerFilter());
            addRow("值:", valueField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            valueField.setText(String.valueOf(((WzLongProperty) prop).getValue()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzLongProperty p)) return "类型不是 Long";
            try {
                p.setValue(Long.parseLong(valueField.getText().trim()));
            } catch (NumberFormatException e) {
                return "值不是有效长整数";
            }
            markChanged(p);
            return null;
        }
    }

    private static final class FloatAdapter extends FieldAdapter {
        private final JTextField valueField = new JTextField(20);

        FloatAdapter() {
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new DecimalFilter());
            addRow("值:", valueField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            valueField.setText(String.valueOf(((WzFloatProperty) prop).getValue()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzFloatProperty p)) return "类型不是 Float";
            try {
                p.setValue(Float.parseFloat(valueField.getText().trim()));
            } catch (NumberFormatException e) {
                return "值不是有效浮点数";
            }
            markChanged(p);
            return null;
        }
    }

    private static final class DoubleAdapter extends FieldAdapter {
        private final JTextField valueField = new JTextField(24);

        DoubleAdapter() {
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new DecimalFilter());
            addRow("值:", valueField);
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            valueField.setText(String.valueOf(((WzDoubleProperty) prop).getValue()));
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzDoubleProperty p)) return "类型不是 Double";
            try {
                p.setValue(Double.parseDouble(valueField.getText().trim()));
            } catch (NumberFormatException e) {
                return "值不是有效双精度数";
            }
            markChanged(p);
            return null;
        }
    }

    private static final class StringAdapter extends FieldAdapter {
        private final JTextArea area = new JTextArea(6, 28);

        StringAdapter() {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            addRow("值:", new JScrollPane(area));
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            area.setText(((WzStringProperty) prop).getValue());
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzStringProperty p)) return "类型不是 String";
            p.setValue(area.getText());
            markChanged(p);
            return null;
        }
    }

    private static final class LuaAdapter extends FieldAdapter {
        private final JTextArea area = new JTextArea(8, 32);

        LuaAdapter() {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            addRow("Lua:", new JScrollPane(area));
        }

        @Override
        public void loadFrom(WzImageProperty prop) {
            area.setText(((WzLuaProperty) prop).getString());
        }

        @Override
        public String copyValuesTo(WzImageProperty target) {
            if (!(target instanceof WzLuaProperty p)) return "类型不是 Lua";
            p.setString(area.getText());
            markChanged(p);
            return null;
        }
    }
}
