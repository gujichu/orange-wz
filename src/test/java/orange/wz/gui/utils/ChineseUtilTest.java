package orange.wz.gui.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChineseUtilTest {

    @Test
    void isChineseStr_nullOrEmpty() {
        assertFalse(ChineseUtil.isChineseStr(null));
        assertFalse(ChineseUtil.isChineseStr(""));
    }

    @Test
    void isChineseStr_chineseWithoutKorean() {
        assertTrue(ChineseUtil.isChineseStr("你好世界"));
        assertFalse(ChineseUtil.isChineseStr("Hello"));
        assertFalse(ChineseUtil.isChineseStr("안녕")); // Korean only
        assertFalse(ChineseUtil.isChineseStr("你好안녕")); // mixed Chinese + Korean
    }
}
