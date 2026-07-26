package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.apache.http.client.methods.RequestBuilder;

/**
 * 推送到 Server 酱 Turbo（微信）。
 * <p>
 * 旧版 Server 酱 {@code sc.ftqq.com} 已经下线，现在只保留 Turbo 版的 {@code sctapi.ftqq.com}。
 *
 * @author sh4wnzec
 * @Time 2020-12-25
 */
public final class SendServerChan {

    private SendServerChan() {
    }

    /**
     * 发送运行结果。
     *
     * @param sendKey Server 酱 Turbo 的 SendKey
     */
    public static void send(String sendKey) {
        JSONObject params = new JSONObject();
        params.put("title", "BilibiliTask 运行结果");
        params.put("desp", ReadLog.getMarkDownString("logs/logback.log"));

        Notifier.send("Server酱", RequestBuilder.post()
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .setUri("https://sctapi.ftqq.com/" + sendKey + ".send")
                .addParameters(Request.pairs(params))
                .build());
    }
}
