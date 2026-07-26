package top.srcrs.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StringUtil} 的测试。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class StringUtilTest {

    @Test
    @DisplayName("正常长度的用户名只露出首尾各一个字")
    void hideStringKeepsEnds() {
        assertEquals("张***三", StringUtil.hideString("张一二三四三", 1, 1, '*'));
        assertEquals("a***f", StringUtil.hideString("abcdef", 1, 1, '*'));
    }

    @Test
    @DisplayName("用户名太短时整串打码，而且不会卡死")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void hideStringHandlesShortInput() {
        // 老实现在这里会算出负数长度，然后 length-- != 0 一路减到 int 下溢
        assertEquals("**", StringUtil.hideString("ab", 1, 1, '*'));
        assertEquals("*", StringUtil.hideString("a", 1, 1, '*'));
        assertEquals("", StringUtil.hideString("", 1, 1, '*'));
        assertEquals("", StringUtil.hideString(null, 1, 1, '*'));
    }

    @Test
    @DisplayName("空白判断覆盖 null、空串和纯空格")
    void blankChecks() {
        assertTrue(StringUtil.isBlank(null));
        assertTrue(StringUtil.isBlank(""));
        assertTrue(StringUtil.isBlank("   "));
        assertFalse(StringUtil.isBlank("a"));
        assertTrue(StringUtil.isAnyBlank("a", ""));
        assertFalse(StringUtil.isAnyBlank("a", "b"));
    }

    @Test
    @DisplayName("null 转成空串，避免拼出字面量 null")
    void getNeverReturnsNull() {
        assertEquals("", StringUtil.get(null));
        assertEquals("1", StringUtil.get(1));
        assertEquals("", StringUtil.trimToEmpty(null));
        assertEquals("a", StringUtil.trimToEmpty("  a "));
    }
}
