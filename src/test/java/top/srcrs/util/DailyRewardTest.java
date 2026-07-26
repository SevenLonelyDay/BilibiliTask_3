package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每日任务完成情况的字段归一化测试。
 * <p>
 * 这里守的是一个花过真金白银的 bug：换接口之后字段名从 {@code coins_av} 变成了 {@code coins}，
 * 代码却还在读旧名字，于是"今日已投币"永远是 0，每次运行都会把 5 个币重新投一遍。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class DailyRewardTest {

    @Test
    @DisplayName("认得 x/member/web/exp/reward 的新字段名")
    void normalizeNewSchema() {
        JSONObject data = new JSONObject();
        data.put("login", true);
        data.put("watch", true);
        data.put("share", false);
        data.put("coins", 50);

        JSONObject result = DailyReward.normalize(data);
        assertTrue(result.getBooleanValue("login"));
        assertTrue(result.getBooleanValue("watch"));
        assertFalse(result.getBooleanValue("share"));
        assertEquals(50, result.getIntValue("coins"));
    }

    @Test
    @DisplayName("也认得旧接口的 *_av 字段名")
    void normalizeLegacySchema() {
        JSONObject data = new JSONObject();
        data.put("login", true);
        data.put("watch_av", true);
        data.put("share_av", true);
        data.put("coins_av", 20);

        JSONObject result = DailyReward.normalize(data);
        assertTrue(result.getBooleanValue("watch"));
        assertTrue(result.getBooleanValue("share"));
        assertEquals(20, result.getIntValue("coins"));
    }

    @Test
    @DisplayName("字段缺失或整个对象为空时按未完成处理")
    void normalizeMissingFields() {
        JSONObject result = DailyReward.normalize(new JSONObject());
        assertFalse(result.getBooleanValue("login"));
        assertFalse(result.getBooleanValue("watch"));
        assertFalse(result.getBooleanValue("share"));
        assertEquals(0, result.getIntValue("coins"));

        JSONObject fromNull = DailyReward.normalize(null);
        assertFalse(fromNull.getBooleanValue("watch"));
        assertEquals(0, fromNull.getIntValue("coins"));
    }

    @Test
    @DisplayName("known 能区分「确认没做」和「根本没问到」")
    void knownFlagSeparatesUnknownFromIncomplete() {
        // 没问到：coins 同样是 0，但不能据此认为今天一个币都没投
        assertFalse(DailyReward.normalize(null).getBooleanValue("known"));
        assertFalse(DailyReward.normalize(new JSONObject()).getBooleanValue("known"));

        JSONObject data = new JSONObject();
        data.put("login", true);
        data.put("coins", 0);
        assertTrue(DailyReward.normalize(data).getBooleanValue("known"));
        assertEquals(0, DailyReward.normalize(data).getIntValue("coins"));
    }
}
