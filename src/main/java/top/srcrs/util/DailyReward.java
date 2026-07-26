package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * 每日经验任务的完成情况。
 * <p>
 * 这里有一处很隐蔽的坑：{@code x/member/web/exp/reward} 返回的字段是
 * {@code login / watch / share / coins}，而旧接口 {@code account.bilibili.com/home/reward}
 * 用的是 {@code watch_av / share_av / coins_av}。之前换了接口却没改字段名，
 * 于是"今日已投币"永远读成 0，每次运行都会重新投满 5 个币。
 * <p>
 * 现在两种字段名都认，并且结果在一次运行内缓存，观看任务和投币任务共用。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class DailyReward {

    private DailyReward() {
    }

    private static JSONObject cache;

    /**
     * 取今日任务完成情况。
     *
     * @return 含 {@code login} / {@code watch} / {@code share} / {@code coins} 四个字段的对象
     */
    public static synchronized JSONObject get() {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    /**
     * 清掉缓存，投币之后想重新确认时可以调用。
     */
    public static synchronized void invalidate() {
        cache = null;
    }

    private static JSONObject load() {
        JSONObject data = fetch(BiliApi.EXP_REWARD);
        if (data == null) {
            log.debug("经验任务接口不可用，回退到旧接口");
            data = fetch(BiliApi.LEGACY_HOME_REWARD);
        }
        if (data == null) {
            log.warn("⚠️无法获取每日任务完成情况，将按全部未完成处理");
            return normalize(new JSONObject());
        }
        return normalize(data);
    }

    private static JSONObject fetch(String url) {
        JSONObject response = Request.get(url);
        if (Request.code(response) != 0) {
            return null;
        }
        return response.getJSONObject("data");
    }

    /**
     * 把两种字段命名统一成一种。
     *
     * @param data 接口返回的 data 节点
     * @return 归一化后的结果
     */
    static JSONObject normalize(JSONObject data) {
        JSONObject result = new JSONObject();
        result.put("login", bool(data, "login"));
        result.put("watch", bool(data, "watch") || bool(data, "watch_av"));
        result.put("share", bool(data, "share") || bool(data, "share_av"));
        result.put("coins", Math.max(intValue(data, "coins"), intValue(data, "coins_av")));
        return result;
    }

    private static boolean bool(JSONObject data, String key) {
        return data != null && data.getBooleanValue(key, false);
    }

    private static int intValue(JSONObject data, String key) {
        return data == null ? 0 : data.getIntValue(key, 0);
    }
}
