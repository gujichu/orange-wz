package orange.wz.gui.utils;

import orange.wz.gui.component.form.data.SearchResult;

import java.util.Locale;

public final class SearchMatcher {
    private SearchMatcher() {
    }

    public static boolean matches(SearchResult item, String search, boolean nameMod, boolean valueMod, boolean equalMod, boolean lowMod) {
        if (search == null || search.isBlank()) {
            return false;
        }
        String needle = normalize(search, lowMod);
        return (nameMod && matchesText(item.name(), needle, equalMod, lowMod))
                || (valueMod && matchesText(item.value(), needle, equalMod, lowMod));
    }

    private static boolean matchesText(String text, String needle, boolean equalMod, boolean lowMod) {
        if (text == null || text.isEmpty() || needle == null || needle.isEmpty()) {
            return false;
        }
        String haystack = normalize(text, lowMod);
        return equalMod ? haystack.equals(needle) : haystack.contains(needle);
    }

    private static String normalize(String value, boolean lowMod) {
        return lowMod ? value.toLowerCase(Locale.ROOT) : value;
    }
}