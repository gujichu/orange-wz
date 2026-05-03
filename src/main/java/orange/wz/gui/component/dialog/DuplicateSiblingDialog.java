package orange.wz.gui.component.dialog;

import orange.wz.gui.utils.JMessageUtil;

import javax.swing.*;
import java.awt.*;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class DuplicateSiblingDialog {

    /**
     * @param row1Trimmed   数量为 1 时为完整节点名；数量&gt;1 时为名称前缀
     * @param count         复制数量
     * @param batchStartInclusive 数量&gt;1 时第一条生成的数字后缀（effect + 6 + 数量3 → effect6,effect7,effect8）；数量为 1 时无效
     */
    public record Result(String row1Trimmed, int count, int batchStartInclusive) {}

    /**
     * @param siblingNameTaken 同级是否已有该名称（不含即将生成的克隆上的原名）
     */
    public static Optional<Result> show(Component parent, String selectedNodeName, Predicate<String> siblingNameTaken) {
        String strippedBase = stripTrailingDigits(selectedNodeName);

        JLabel row1Label = new JLabel();
        JTextField row1Field = new JTextField(24);
        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));

        AtomicInteger batchStartInclusive = new AtomicInteger(1);
        AtomicInteger lastCount = new AtomicInteger(1);

        row1Label.setText("节点名称");
        row1Field.setText(findFirstFreeNumberedName(strippedBase, siblingNameTaken));

        countSpinner.addChangeListener(e -> {
            int count = ((Number) countSpinner.getValue()).intValue();
            int prev = lastCount.getAndSet(count);

            if (prev == 1 && count > 1) {
                // 从单名称切到批量：用当前输入解析前缀与起始序号（effect6 → 前缀 effect，起始 6）
                ParsedPrefixNumber p = parseTrailingPositiveInteger(row1Field.getText().trim());
                row1Label.setText("名称前缀");
                row1Field.setText(p.prefix());
                batchStartInclusive.set(p.startInclusive());
            } else if (prev > 1 && count == 1) {
                batchStartInclusive.set(1);
                row1Label.setText("节点名称");
                row1Field.setText(findFirstFreeNumberedName(strippedBase, siblingNameTaken));
            } else if (count > 1) {
                row1Label.setText("名称前缀");
            } else {
                row1Label.setText("节点名称");
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(row1Label, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(row1Field, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("复制数量"), gbc);
        gbc.gridx = 1;
        panel.add(countSpinner, gbc);

        int ok = JOptionPane.showConfirmDialog(parent, panel, "同节点复制",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }

        try {
            countSpinner.commitEdit();
        } catch (ParseException ignored) {
        }

        String row1 = row1Field.getText().trim();
        if (row1.isEmpty()) {
            JMessageUtil.error("名称不能为空");
            return Optional.empty();
        }

        int count = ((Number) countSpinner.getValue()).intValue();
        int batchStart = count > 1 ? batchStartInclusive.get() : 1;
        return Optional.of(new Result(row1, count, batchStart));
    }

    /** 去掉末尾连续数字，用于批量前缀（effect12 → effect）；若无字母前缀则退回原名 */
    static String stripTrailingDigits(String name) {
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        String s = name.substring(0, end);
        return s.isEmpty() ? name : s;
    }

    private static String findFirstFreeNumberedName(String base, Predicate<String> taken) {
        for (int k = 1; k < 100_000; k++) {
            String cand = base + k;
            if (!taken.test(cand)) {
                return cand;
            }
        }
        return base + System.currentTimeMillis();
    }

    /**
     * 解析末尾非负整数：effect6 → prefix=effect, start=6；无前缀数字 effect → prefix=effect, start=1
     */
    private static ParsedPrefixNumber parseTrailingPositiveInteger(String s) {
        if (s.isEmpty()) {
            return new ParsedPrefixNumber("", 1);
        }
        int end = s.length();
        while (end > 0 && Character.isDigit(s.charAt(end - 1))) {
            end--;
        }
        String prefixPart = s.substring(0, end);
        String numPart = s.substring(end);
        if (numPart.isEmpty()) {
            return new ParsedPrefixNumber(s, 1);
        }
        try {
            int n = Integer.parseInt(numPart);
            if (n < 1) {
                n = 1;
            }
            if (prefixPart.isEmpty()) {
                return new ParsedPrefixNumber(s, 1);
            }
            return new ParsedPrefixNumber(prefixPart, n);
        } catch (NumberFormatException e) {
            return new ParsedPrefixNumber(s, 1);
        }
    }

    private record ParsedPrefixNumber(String prefix, int startInclusive) {}
}
