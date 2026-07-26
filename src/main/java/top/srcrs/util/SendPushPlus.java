package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;

import java.nio.charset.StandardCharsets;

/**
 * 推送到 pushplus。
 * <p>
 * 老域名 {@code pushplus.hxtrip.com} 已经停用，现在的地址是 {@code www.pushplus.plus}，
 * 并且改成 POST + JSON，日志内容再长也不会被 URL 长度限制截断。
 *
 * @author sixer
 * @Time 2020-10-22
 */
public final class SendPushPlus {

    private SendPushPlus() {
    }

    /**
     * 发送运行结果。
     *
     * @param token pushplus 的 token
     */
    public static void send(String token) {
        JSONObject body = new JSONObject();
        body.put("token", token);
        body.put("title", "BilibiliTask运行结果");
        body.put("content", ReadLog.getHTMLString("logs/logback.log"));
        body.put("template", "html");

        Notifier.send("PUSH+", RequestBuilder.post()
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .setUri("https://www.pushplus.plus/send")
                .setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8))
                .build());
    }
}
