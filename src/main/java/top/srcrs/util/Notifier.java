package top.srcrs.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

/**
 * 推送渠道共用的发送逻辑。
 * <p>
 * 各个渠道原本各写一遍 httpclient 样板，而且只看 HTTP 状态码——推送服务返回 200 加一段错误 JSON 时
 * 依然会报"推送正常"。这里统一处理，并把响应体一并打出来方便排查。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class Notifier {

    private Notifier() {
    }

    /** 响应体日志的截断长度 */
    private static final int MAX_LOG_BODY = 200;

    /**
     * 执行一次推送请求并输出结果。
     *
     * @param channel 渠道名，用于日志
     * @param request 已经构造好的请求
     */
    public static void send(String channel, HttpUriRequest request) {
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (status == HttpStatus.SC_OK) {
                log.info("【{}推送】: 正常✔", channel);
                log.debug("【{}推送】响应: {}", channel, truncate(body));
            } else {
                log.warn("【{}推送】: 失败, HTTP {} {}❌", channel, status, truncate(body));
            }
        } catch (Exception e) {
            log.error("💔{}推送错误 : ", channel, e);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String single = body.replace('\n', ' ');
        return single.length() <= MAX_LOG_BODY ? single : single.substring(0, MAX_LOG_BODY) + "...";
    }
}
