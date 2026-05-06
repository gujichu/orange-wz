package orange.wz.gui.utils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按预览项节点名（动画列表名或单图节点名）判断是否应显示。
 */
public final class PreviewNameFilter {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Set<String> ICON_NAMES = Set.of(
            "icon", "icondisabled", "iconmouseover"
    );

    private PreviewNameFilter() {
    }

    public static boolean shouldInclude(String name, AnimationPreviewOptions opts) {
        if (name == null || opts == null) {
            return true;
        }
        String n = name.trim();
        if (n.isEmpty()) {
            return true;
        }
        if (isIconName(n)) {
            return opts.isIncludeIconNamed();
        }
        if (NUMBER_PATTERN.matcher(n).matches()) {
            return opts.isIncludeNumericNamed();
        }
        return opts.isIncludeEnglishNamed();
    }

    private static boolean isIconName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return ICON_NAMES.contains(key);
    }
}
