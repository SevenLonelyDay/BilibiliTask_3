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

    /**
     * 今日投币已获得的经验（0 ~ 50）。
     * <p>
     * 优先走专用接口 {@code x/web-interface/coin/today/exp}，它只回一个数字，比从
     * {@code exp/reward} 里挑字段稳。这个值直接决定今天还要投几个币，读错就会重复花硬币，
     * 所以多留一条独立的来源。
     *
     * @return 今日投币获得的经验；两个来源都拿不到时返回 -1，调用方应当据此跳过投币
     */
    public static int coinExp() {
        JSONObject response = Request.get(BiliApi.COIN_TODAY_EXP);
        if (Request.code(response) == 0 && response.containsKey("data")) {
            return response.getIntValue("data", 0);
        }
        log.debug("投币经验专用接口不可用，改从每日任务状态里取");

        JSONObject reward = get();
        if (reward.getBooleanValue("known", false)) {
            return reward.getIntValue("coins");
        }
        log.warn("⚠️无法确认今日已投币数量，本次跳过投币以免重复消耗硬币");
        return -1;
    }

    private static JSONObject fetch(String url) {
        JSONObject response = Request.get(url, new JSONObject(), BiliApi.REFERER_ACCOUNT_HOME);
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
        // known 用来区分"确认没做"和"根本没问到"，后者不能当成没做，否则会重复投币
        result.put("known", data != null && !data.isEmpty());
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
