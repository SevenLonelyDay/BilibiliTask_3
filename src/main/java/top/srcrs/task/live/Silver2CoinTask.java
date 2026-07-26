package top.srcrs.task.live;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;

/**
 * 银瓜子兑换硬币。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class Silver2CoinTask implements Task {

    private final UserData userData = UserData.getInstance();
    private final Config config = Config.getInstance();

    /** 兑换一个硬币需要的银瓜子 */
    private static final int SILVER_PER_COIN = 700;

    @Override
    public void run() {
        int silver = getSilver();
        log.info("【银瓜子】: {}", silver);

        if (!config.isS2c()) {
            log.info("【银瓜子兑换硬币】: 自定义配置不将银瓜子兑换硬币✔");
            return;
        }
        if (silver < SILVER_PER_COIN) {
            log.info("【银瓜子兑换硬币】: 银瓜子余额不足❌");
            return;
        }
        log.info("【银瓜子兑换硬币】: {}", exchange());
    }

    /**
     * 兑换硬币。
     * <p>
     * {@code pay/v1/Exchange/silver2coin} 是老地址，现在走 {@code xlive/revenue}，
     * 老地址保留作兜底。
     *
     * @return 展示用的结果文案
     */
    private String exchange() {
        JSONObject params = new JSONObject();
        params.put("csrf", userData.getBiliJct());
        params.put("csrf_token", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.LIVE_SILVER2COIN, params, BiliApi.REFERER_LIVE_LINK);
        if (Request.code(response) != 0) {
            log.debug("新版兑换接口返回 {}，尝试旧接口", response.getString("code"));
            response = Request.post(BiliApi.LIVE_SILVER2COIN_LEGACY, params, BiliApi.REFERER_LIVE_LINK);
        }
        if (Request.code(response) == 0) {
            String message = response.getString("msg");
            if (StringUtil.isBlank(message)) {
                message = response.getString("message");
            }
            return StringUtil.isBlank(message) ? "成功✔" : message + "✔";
        }
        return response.getString("message") + "❌";
    }

    /**
     * 查询银瓜子余额。
     *
     * @return 银瓜子数量，查不到时为 0
     */
    private int getSilver() {
        JSONObject response = Request.get(BiliApi.LIVE_USER_INFO, new JSONObject(), BiliApi.REFERER_LIVE_LINK);
        if (Request.code(response) != 0) {
            log.warn("⚠️获取银瓜子余额失败: {}", response.getString("message"));
            return 0;
        }
        JSONObject data = response.getJSONObject("data");
        return data == null ? 0 : data.getIntValue("silver", 0);
    }
}
