package top.srcrs.util;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.domain.UserData;

/**
 * 账号信息。
 * <p>
 * {@code nav} 接口一次就能给出用户名、硬币、经验、大会员状态、B 币券余额和 WBI 密钥，
 * 所有需要"重新看一眼当前状态"的地方都走这里，避免各处各拉一遍。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class Account {

    private Account() {
    }

    private static final UserData USER_DATA = UserData.getInstance();

    /** Cookie 失效 */
    private static final int NOT_LOGGED_IN = -101;

    /**
     * 拉取并刷新账号信息。
     *
     * @return 账号可用返回 true
     */
    public static boolean refresh() {
        JSONObject response = Request.get(BiliApi.NAV);
        int code = Request.code(response);
        if (code == NOT_LOGGED_IN) {
            log.error("💔账户已失效，请在 Secrets 中重新绑定你的 Cookie");
            return false;
        }
        if (code != 0) {
            log.error("💔账户验证失败: {} - {}", code, Request.message(response));
            return false;
        }

        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            log.error("💔账户验证失败: 接口没有返回数据");
            return false;
        }

        // 顺手把 WBI 密钥收下，后面的签名请求就不用再单独拉一次 nav
        WbiSignature.updateFromNav(data);

        USER_DATA.setUname(data.getString("uname"));
        USER_DATA.setMid(data.getString("mid"));
        USER_DATA.setVipType(data.getString("vipType"));
        USER_DATA.setVipStatus(data.getString("vipStatus"));
        USER_DATA.setMoney(data.getBigDecimal("money"));

        JSONObject levelInfo = data.getJSONObject("level_info");
        if (levelInfo != null) {
            USER_DATA.setCurrentExp(levelInfo.getIntValue("current_exp", 0));
            USER_DATA.setNextExp(levelInfo.getString("next_exp"));
            USER_DATA.setCurrentLevel(levelInfo.getString("current_level"));
        }

        JSONObject wallet = data.getJSONObject("wallet");
        USER_DATA.setCouponBalance(wallet == null ? 0 : wallet.getIntValue("coupon_balance", 0));
        return true;
    }
}
