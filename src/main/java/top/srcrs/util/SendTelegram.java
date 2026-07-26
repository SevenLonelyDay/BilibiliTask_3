package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;

import java.nio.charset.StandardCharsets;

/**
 * 推送到 Telegram Bot。
 *
 * @author qiwihui
 * @Time 2020-12-16
 */
public final class SendTelegram {

    private SendTelegram() {
    }

    /** Telegram 单条消息的长度上限是 4096 个字符 */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    /**
     * 发送运行结果。
     *
     * @param botToken 机器人 Token
     * @param chatId   会话 ID
     */
    public static void send(String botToken, String chatId) {
        String text = "BilibiliTask运行结果:\n" + ReadLog.getMarkDownString("logs/logback.log");
        if (text.length() > MAX_MESSAGE_LENGTH) {
            // 超长会被 Telegram 直接拒收，保留结尾的汇总部分
            text = "...\n" + text.substring(text.length() - MAX_MESSAGE_LENGTH);
        }

        JSONObject body = new JSONObject();
        body.put("chat_id", chatId);
        body.put("text", text);

        Notifier.send("Telegram", RequestBuilder.post()
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .setUri("https://api.telegram.org/bot" + botToken + "/sendMessage")
                .setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8))
                .build());
    }
}
