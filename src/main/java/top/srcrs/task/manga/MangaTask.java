package top.srcrs.task.manga;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;

import java.util.Locale;

/**
 * 漫画签到。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class MangaTask implements Task {

    private final Config config = Config.getInstance();

    @Override
    public void run() {
        if (!config.isManga()) {
            log.info("【漫画签到】: 自定义配置不执行漫画签到任务✔");
            return;
        }

        String platform = platform();
        log.info("【漫画签到设备信息】: {}", platform);

        JSONObject params = new JSONObject();
        params.put("platform", platform);
        // platform 要跟在地址后面，放进表单体这个接口读不到
        JSONObject response = Request.postQuery(BiliApi.MANGA_CLOCK_IN, params, BiliApi.REFERER_MANGA);

        if (Request.code(response) == 0) {
            log.info("【漫画签到】: 成功✔");
            return;
        }
        // 漫画那套接口出错时 code 可能是 "invalid_argument" 这类字符串，只能靠文案判断
        String message = Request.message(response);
        if (isDuplicate(response, message)) {
            log.info("【漫画签到】: 今天已经签过了❌");
            return;
        }
        log.warn("【漫画签到】: 失败, {}❌", StringUtil.isBlank(message) ? response.getString("code") : message);
    }

    private boolean isDuplicate(JSONObject response, String message) {
        if (message.toLowerCase(Locale.ROOT).contains("duplicate")) {
            return true;
        }
        // 早期版本返回的重复签到码
        return "1".equals(StringUtil.trimToEmpty(response.getString("code")));
    }

    /**
     * 读取配置里的设备标识。
     *
     * @return android / ios，配置缺失时默认 android
     */
    private String platform() {
        String platform = StringUtil.trimToEmpty(config.getPlatform());
        return StringUtil.isBlank(platform) ? "android" : platform;
    }
}
