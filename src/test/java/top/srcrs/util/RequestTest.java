package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 响应解析相关的测试，不发真实请求。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class RequestTest {

    @Test
    @DisplayName("数字和数字字符串的 code 都能读出来")
    void codeFromNumberAndString() {
        assertEquals(0, Request.code(JSONObject.of("code", 0)));
        assertEquals(-101, Request.code(JSONObject.of("code", -101)));
        assertEquals(1, Request.code(JSONObject.of("code", "1")));
    }

    @Test
    @DisplayName("漫画接口的字符串 code 不会把程序搞崩")
    void codeFromNonNumericString() {
        // manga 的 twirp 接口出错时 code 会是 "invalid_argument"
        assertEquals(Request.TRANSPORT_ERROR, Request.code(JSONObject.of("code", "invalid_argument")));
        assertEquals(Request.TRANSPORT_ERROR, Request.code(new JSONObject()));
        assertEquals(Request.TRANSPORT_ERROR, Request.code(null));
    }

    @Test
    @DisplayName("message 与 msg 两种字段名都能取到")
    void messageFallsBackToMsg() {
        assertEquals("ok", Request.message(JSONObject.of("message", "ok")));
        assertEquals("ok", Request.message(JSONObject.of("msg", "ok")));
        assertEquals("", Request.message(new JSONObject()));
        assertEquals("", Request.message(null));
    }

    @Test
    @DisplayName("构造出来的失败结果带 -1 和原因")
    void errorResult() {
        JSONObject error = Request.error("boom");
        assertEquals(Request.TRANSPORT_ERROR, Request.code(error));
        assertEquals("boom", Request.message(error));
    }

    @Test
    @DisplayName("参数转键值对时 null 值不会变成字面量 null")
    void pairsHandlesNullValue() {
        JSONObject params = new JSONObject();
        params.put("a", "1");
        params.put("b", null);
        assertEquals(2, Request.pairs(params).length);
        assertEquals("", Request.pairs(params)[1].getValue());
    }
}
