package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WBI 签名算法的回归测试。
 * <p>
 * 用的是 bilibili-API-collect 文档里公开的那组示例值：只要签名算法被改坏，这里一定会红。
 * 之前的实现漏掉了参数值的 URL 编码，还多出一个并不存在的 {@code w_ks} 参数，
 * 有了这组用例就不至于再悄无声息地跑偏。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class WbiSignatureTest {

    private static final String IMG_KEY = "7cd084941338484aae1ad9425b84077c";
    private static final String SUB_KEY = "4932caff0ff746eab6f01bf08b70ac45";
    private static final String MIXIN_KEY = "ea1db124af3c7062474693fa704f4ff8";

    @Test
    @DisplayName("乱序表拼出的 mixin_key 与官方示例一致")
    void mixinKeyMatchesReference() {
        assertEquals(MIXIN_KEY, WbiSignature.mixinKey(IMG_KEY + SUB_KEY));
    }

    @Test
    @DisplayName("mixin_key 恒为 32 位")
    void mixinKeyLength() {
        assertEquals(32, WbiSignature.mixinKey(IMG_KEY + SUB_KEY).length());
    }

    @Test
    @DisplayName("w_rid 与官方示例一致")
    void wRidMatchesReference() {
        JSONObject params = new JSONObject();
        params.put("foo", "114");
        params.put("bar", "514");
        params.put("zab", "1919810");
        params.put("wts", "1702204169");

        assertEquals("8f6f2b5b3d485fe1886cec6a0be8c5d4", WbiSignature.wRid(params, MIXIN_KEY));
    }

    @Test
    @DisplayName("查询串按 key 升序排列")
    void querySorted() {
        JSONObject params = new JSONObject();
        params.put("foo", "114");
        params.put("bar", "514");
        params.put("zab", "1919810");
        params.put("wts", "1702204169");

        assertEquals("bar=514&foo=114&wts=1702204169&zab=1919810", WbiSignature.buildQuery(params));
    }

    @Test
    @DisplayName("参数值按 encodeURIComponent 编码，并剔除 !'()* ")
    void queryEncodesValues() {
        JSONObject params = new JSONObject();
        params.put("keyword", "one two");
        params.put("amp", "a&b");
        params.put("noisy", "he!l(l)o*");

        assertEquals("amp=a%26b&keyword=one%20two&noisy=hello", WbiSignature.buildQuery(params));
    }

    @Test
    @DisplayName("空格编码成 %20 而不是 +，波浪号不转义")
    void encodeFollowsEncodeUriComponent() {
        assertEquals("a%20b", WbiSignature.encode("a b"));
        assertEquals("~", WbiSignature.encode("~"));
        assertEquals("%E4%B8%AD", WbiSignature.encode("中"));
    }

    @Test
    @DisplayName("从密钥图片地址里取出文件名")
    void updateFromNavParsesKeys() {
        JSONObject wbiImg = new JSONObject();
        wbiImg.put("img_url", "https://i0.hdslb.com/bfs/wbi/" + IMG_KEY + ".png");
        wbiImg.put("sub_url", "https://i0.hdslb.com/bfs/wbi/" + SUB_KEY + ".png");
        JSONObject navData = new JSONObject();
        navData.put("wbi_img", wbiImg);

        WbiSignature.updateFromNav(navData);

        JSONObject params = new JSONObject();
        params.put("foo", "114");
        params.put("bar", "514");
        params.put("zab", "1919810");
        params.put("wts", "1702204169");
        // 密钥被正确解析出来的话，签名结果应该和官方示例对上
        assertEquals("8f6f2b5b3d485fe1886cec6a0be8c5d4", WbiSignature.wRid(params, MIXIN_KEY));
    }

    @Test
    @DisplayName("MD5 输出 32 位小写十六进制")
    void md5Format() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", WbiSignature.md5(""));
    }

    @Test
    @DisplayName("签名后的查询串自带 wts，并以 w_rid 结尾")
    void signedQueryShape() {
        // 先喂进密钥，避免这里去请求 nav 接口
        JSONObject wbiImg = new JSONObject();
        wbiImg.put("img_url", "https://i0.hdslb.com/bfs/wbi/" + IMG_KEY + ".png");
        wbiImg.put("sub_url", "https://i0.hdslb.com/bfs/wbi/" + SUB_KEY + ".png");
        JSONObject navData = new JSONObject();
        navData.put("wbi_img", wbiImg);
        WbiSignature.updateFromNav(navData);

        JSONObject params = new JSONObject();
        params.put("mid", "477137547");

        String query = WbiSignature.signedQuery(params);
        assertTrue(query.startsWith("mid=477137547&wts="), "实际为: " + query);

        String[] parts = query.split("&w_rid=");
        assertEquals(2, parts.length);
        assertEquals(32, parts[1].length());
        // 签名必须是对"发出去的那串查询串"算的，否则服务端一定校验不过
        assertEquals(parts[1], WbiSignature.md5(parts[0] + MIXIN_KEY));
    }
}
