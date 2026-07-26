package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.domain.UserData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 生成 bili_ticket。
 * <p>
 * 带上这个 Cookie 能明显降低被风控拦下的概率。签名规则是对 {@code ts<时间戳>} 做
 * HMAC-SHA256，密钥是 web 端 JS 里写死的 {@code XgwSnGZ1p}。
 * <p>
 * 注意参数必须放在查询串里：早先的实现把它们塞进了表单体，服务端一直回
 * {@code -400 empty `ts` field}，等于这个 Cookie 从来没生效过。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class BiliTicket {

    private BiliTicket() {
    }

    private static final String HMAC_KEY = "XgwSnGZ1p";
    private static final String KEY_ID = "ec02";

    /**
     * 取一个 bili_ticket。
     *
     * @return ticket；获取失败时返回空串，调用方按"没有这个 Cookie"处理
     */
    public static String fetch() {
        long ts = System.currentTimeMillis() / 1000;
        String hexSign = hmacSha256("ts" + ts, HMAC_KEY);
        if (StringUtil.isBlank(hexSign)) {
            return "";
        }

        JSONObject params = new JSONObject();
        params.put("key_id", KEY_ID);
        params.put("hexsign", hexSign);
        params.put("context[ts]", String.valueOf(ts));
        params.put("csrf", StringUtil.trimToEmpty(UserData.getInstance().getBiliJct()));

        JSONObject response = Request.postQuery(BiliApi.GEN_WEB_TICKET, params);
        if (Request.code(response) == 0) {
            JSONObject data = response.getJSONObject("data");
            String ticket = data == null ? null : data.getString("ticket");
            if (StringUtil.isNotBlank(ticket)) {
                log.debug("bili_ticket 获取成功");
                return ticket;
            }
        }
        log.warn("⚠️bili_ticket 获取失败: {} - {}",
                response.getString("code"), response.getString("message"));
        return "";
    }

    /**
     * HMAC-SHA256 并转成小写十六进制。
     *
     * @param data 待签名内容
     * @param key  密钥
     * @return 十六进制签名；失败时返回空串
     */
    static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("💔bili_ticket 签名失败: ", e);
            return "";
        }
    }
}
