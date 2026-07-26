package top.srcrs.util;

import java.util.Arrays;

/**
 * 字符串小工具。
 *
 * @author Ali
 * @Time 2020-11-20
 */
public final class StringUtil {

    private StringUtil() {
    }

    /**
     * 对象转字符串。
     *
     * @param o 任意对象
     * @return 字符串形式；null 转成空串，避免拼参数时出现字面量 "null"
     */
    public static String get(Object o) {
        return o == null ? "" : o.toString();
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 判断是否为空白。
     *
     * @param cs 待判断内容
     * @return null、空串、纯空格都算空白
     */
    public static boolean isBlank(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return true;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAnyBlank(CharSequence... cs) {
        return Arrays.stream(cs).anyMatch(StringUtil::isBlank);
    }

    /**
     * 去掉首尾空白，null 视作空串。
     *
     * @param value 原始值
     * @return 处理后的值
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 打码，只保留首尾若干字符。
     * <p>
     * 老实现在字符串比 {@code startLen + endLen} 还短时会算出负数长度，
     * 循环条件又写成 {@code length-- != 0}，会一路减到 int 下溢，等于挂死。
     *
     * @param str         原始字符串
     * @param startLen    保留的开头长度
     * @param endLen      保留的结尾长度
     * @param replaceChar 中间用来替换的字符
     * @return 打码后的字符串
     */
    public static String hideString(String str, int startLen, int endLen, char replaceChar) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (startLen < 0 || endLen < 0 || str.length() <= startLen + endLen) {
            // 短到没有可打码的部分，整串替换，免得反而把内容全露出来
            return String.valueOf(replaceChar).repeat(str.length());
        }
        int hidden = Math.min(str.length() - startLen - endLen, 3);
        return str.substring(0, startLen)
                + String.valueOf(replaceChar).repeat(hidden)
                + str.substring(str.length() - endLen);
    }
}
