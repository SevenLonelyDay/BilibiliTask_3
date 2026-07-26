package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * WBI 签名。
 * <p>
 * 2023 年起 B 站给一批 web 接口加上了 WBI 鉴权：从 {@code nav} 接口拿到 img_key / sub_key，
 * 按固定的乱序表拼出 32 位 mixin_key，再把请求参数按 key 排序、URL 编码后拼上 mixin_key 求 MD5，
 * 得到 {@code w_rid}，与 {@code wts} 一起作为参数发出去。
 * <p>
 * 算法说明见 <a href="https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md">wbi.md</a>。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class WbiSignature {

    private WbiSignature() {
    }

    /** 官方 JS 里写死的乱序表 */
    private static final int[] MIX_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
            26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
            20, 34, 44, 52
    };

    /** 参数值里需要先剔除的字符，官方 JS 的行为 */
    private static final String FILTERED_CHARS = "!'()*";

    /** 密钥每天更新一次，本地缓存 30 分钟足够一次运行使用 */
    private static final long KEY_TTL_MS = 30 * 60 * 1000L;

    private static volatile String mixinKey = "";
    private static volatile long keyUpdatedAt = 0L;

    /**
     * 从 nav 接口的返回里提取并缓存 WBI 密钥。
     * <p>
     * 由 {@code BiliStart} 在校验账号时顺手调用，这样整个流程只需要请求一次 nav。
     *
     * @param navData nav 接口 data 节点，允许为 null
     */
    public static void updateFromNav(JSONObject navData) {
        if (navData == null) {
            return;
        }
        JSONObject wbiImg = navData.getJSONObject("wbi_img");
        if (wbiImg == null) {
            return;
        }
        String imgKey = fileName(wbiImg.getString("img_url"));
        String subKey = fileName(wbiImg.getString("sub_url"));
        if (StringUtil.isBlank(imgKey) || StringUtil.isBlank(subKey)) {
            return;
        }
        mixinKey = mixinKey(imgKey + subKey);
        keyUpdatedAt = System.currentTimeMillis();
        log.debug("WBI 密钥已更新");
    }

    /**
     * 生成可以直接拼到地址后面的、已签名的查询串。
     * <p>
     * 返回的是拼好的字符串而不是参数对象：签名是对"编码之后的查询串"求的 MD5，
     * 如果把参数交给 URIBuilder 再编码一遍，编码规则稍有出入（比如空格是 {@code +} 还是 {@code %20}）
     * 签名就对不上了。直接把签名时用的那份字符串发出去，两边永远一致。
     *
     * @param params 原始请求参数，不会被修改
     * @return 形如 {@code a=1&wts=...&w_rid=...} 的查询串；密钥不可用时返回空串
     */
    public static String signedQuery(JSONObject params) {
        String key = currentMixinKey();
        if (StringUtil.isBlank(key)) {
            log.warn("⚠️WBI 密钥不可用，本次请求不带签名发出");
            return "";
        }
        JSONObject signed = new JSONObject();
        if (params != null) {
            signed.putAll(params);
        }
        signed.put("wts", String.valueOf(System.currentTimeMillis() / 1000));

        String query = buildQuery(signed);
        return query + "&w_rid=" + md5(query + key);
    }

    /**
     * 计算 w_rid。抽出来是为了能被单元测试直接验证。
     *
     * @param params    已经含 wts 的参数
     * @param mixinKey  32 位 mixin_key
     * @return 32 位小写 MD5
     */
    static String wRid(JSONObject params, String mixinKey) {
        return md5(buildQuery(params) + mixinKey);
    }

    /**
     * 按 key 升序拼出 URL 编码后的查询串。
     *
     * @param params 请求参数
     * @return 形如 {@code bar=514&foo=114} 的查询串
     */
    static String buildQuery(JSONObject params) {
        Map<String, Object> sorted = new TreeMap<>(params);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(encode(entry.getKey()))
                 .append('=')
                 .append(encode(filter(StringUtil.get(entry.getValue()))));
        }
        return query.toString();
    }

    /**
     * 按乱序表拼出 mixin_key。
     *
     * @param orig img_key + sub_key，共 64 位
     * @return 32 位 mixin_key
     */
    static String mixinKey(String orig) {
        StringBuilder key = new StringBuilder(32);
        for (int index : MIX_KEY_ENC_TAB) {
            if (index < orig.length()) {
                key.append(orig.charAt(index));
            }
            if (key.length() == 32) {
                break;
            }
        }
        return key.toString();
    }

    /**
     * 与 JS 的 {@code encodeURIComponent} 保持一致。
     * <p>
     * {@link URLEncoder} 是 form 编码：空格变 {@code +}、{@code ~} 会被转义，两处都要纠正，
     * 否则签名和服务端算出来的对不上。
     *
     * @param value 待编码的值
     * @return 编码结果
     */
    static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                             .replace("+", "%20")
                             .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 一定存在，这里不可能发生
            throw new IllegalStateException(e);
        }
    }

    /**
     * 剔除官方 JS 会过滤掉的字符。
     *
     * @param value 参数值
     * @return 过滤后的值
     */
    private static String filter(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (FILTERED_CHARS.indexOf(c) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 取当前可用的 mixin_key，必要时补一次 nav 请求。
     *
     * @return mixin_key，取不到时为空串
     */
    private static synchronized String currentMixinKey() {
        boolean expired = System.currentTimeMillis() - keyUpdatedAt > KEY_TTL_MS;
        if (StringUtil.isBlank(mixinKey) || expired) {
            JSONObject nav = Request.get(BiliApi.NAV);
            updateFromNav(nav.getJSONObject("data"));
        }
        return mixinKey;
    }

    /**
     * 从密钥图片地址里取出不带扩展名的文件名。
     *
     * @param url 形如 https://i0.hdslb.com/bfs/wbi/xxx.png
     * @return 文件名，解析不出时为空串
     */
    private static String fileName(String url) {
        if (StringUtil.isBlank(url)) {
            return "";
        }
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (slash < 0 || dot <= slash) {
            return "";
        }
        return url.substring(slash + 1, dot);
    }

    /**
     * 32 位小写 MD5。
     *
     * @param input 输入
     * @return 摘要
     */
    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 必备算法
            throw new IllegalStateException(e);
        }
    }
}
