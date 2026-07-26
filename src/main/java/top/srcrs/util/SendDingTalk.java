package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;

import java.nio.charset.StandardCharsets;

/**
 * 推送到钉钉群机器人。
 *
 * @author srcrs
 * @Time 2020-11-16
 */
public final class SendDingTalk {

    private SendDingTalk() {
    }

    /**
     * 发送运行结果。
     *
     * @param webhook 钉钉群机器人的 webhook 地址
     */
    public static void send(String webhook) {
        JSONObject markdown = new JSONObject();
        markdown.put("title", "BilibiliTask运行结果");
        markdown.put("text", ReadLog.getMarkDownString("logs/logback.log"));

        JSONObject body = new JSONObject();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);

        Notifier.send("钉钉", RequestBuilder.post()
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .setUri(webhook)
                .setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8))
                .build());
    }
}
