package orange.wz.gui.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从对话框多行/单行输入中解析多个节点名称（逗号、分号、换行分隔）。
 */
public final class MultiNodeNameParser {

    private static final Pattern DELIMITERS = Pattern.compile("[,;\\r\\n]+");

    private MultiNodeNameParser() {
    }

    /**
     * @param raw 用户输入的原始文本
     * @return 去空白后的非空名称列表，保持出现顺序
     */
    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = DELIMITERS.split(raw);
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** @return 若存在重复则返回第一个重复的名称，否则 null */
    public static String firstDuplicate(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n)) {
                return n;
            }
        }
        return null;
    }
}
