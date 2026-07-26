package top.srcrs.task.bigvip;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.util.Account;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 月底自动花掉当月的 B 币券，免得过期作废。
 * <p>
 * 支持两种用法：给自己充电，或者兑换成直播用的金瓜子。
 *
 * @author srcrs
 * @Time 2020-10-19
 */
@Slf4j
public class BiCoinApply implements Task {

    private final UserData userData = UserData.getInstance();
    private final Config config = Config.getInstance();

    /** B 币券当月底作废，28 号动手还留有余量 */
    private static final int END_OF_MONTH = 28;
    /** 北京时间 */
    private static final ZoneId CST = ZoneId.of("Asia/Shanghai");
    /** 充电最少需要 2 个 B 币券 */
    private static final int MIN_CHARGE_COUPON = 2;
    /** 充电成功后的订单状态 */
    private static final int ORDER_PAID = 4;

    @Override
    public void run() {
        // 上一个任务刚领完本月的 B 币券，余额要重新读一次
        Account.refresh();
        Integer balance = userData.getCouponBalance();
        int couponBalance = balance == null ? 0 : balance;
        log.info("【B币券】: {}", couponBalance);

        if (couponBalance <= 0) {
            log.info("【使用B币券】: B币券为0 ,无法使用❌");
            return;
        }
        if (LocalDate.now(CST).getDayOfMonth() != END_OF_MONTH) {
            log.info("【使用B币券】: 今日不是月底({}号)❌", END_OF_MONTH);
            return;
        }

        switch (StringUtil.trimToEmpty(config.getAutoBiCoin())) {
            case "1":
                doCharge(couponBalance);
                break;
            case "2":
                doMelonSeed(couponBalance);
                break;
            default:
                log.info("【使用B币券】: 自定义配置不使用");
                break;
        }
    }

    /**
     * 用 B 币券给自己充电。
     *
     * @param couponBalance B 币券余额
     */
    private void doCharge(int couponBalance) {
        if (couponBalance < MIN_CHARGE_COUPON) {
            log.warn("【使用B币券】: B币卷不足 {} 个 ,无法给自己充电❌", MIN_CHARGE_COUPON);
            return;
        }
        String userId = userData.getMid();
        JSONObject params = new JSONObject();
        params.put("bp_num", String.valueOf(couponBalance));
        params.put("is_bp_remains_prior", "true");
        params.put("up_mid", userId);
        params.put("otype", "up");
        params.put("oid", userId);
        params.put("csrf", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.ELEC_PAY_QUICK, params,
                "https://space.bilibili.com/" + userId);
        if (Request.code(response) != 0) {
            log.warn("【使用B币券】: 充电失败, 原因为: {}❌", Request.message(response));
            return;
        }
        JSONObject data = response.getJSONObject("data");
        if (data == null || data.getIntValue("status", -1) != ORDER_PAID) {
            log.warn("【使用B币券】: 充电未完成, 返回: {}❌", data);
            return;
        }
        log.info("【使用B币券】: 本次给自己充值了: {}个电池✔", couponBalance * 10);
        chargeComment(data.getString("order_no"));
    }

    /**
     * 充电之后补一条留言。
     *
     * @param orderNo 订单号
     */
    private void chargeComment(String orderNo) {
        if (StringUtil.isBlank(orderNo)) {
            return;
        }
        JSONObject params = new JSONObject();
        params.put("order_id", orderNo);
        params.put("message", "BilibiliTask自动充电");
        params.put("csrf", userData.getBiliJct());
        Request.post(BiliApi.ELEC_MESSAGE, params);
    }

    /**
     * 用 B 币券兑换金瓜子。
     *
     * @param couponBalance B 币券余额
     */
    private void doMelonSeed(int couponBalance) {
        JSONObject params = new JSONObject();
        params.put("pay_bp", String.valueOf(couponBalance * 1000L));
        params.put("context_id", "1");
        params.put("context_type", "11");
        params.put("goods_id", "1");
        params.put("goods_num", String.valueOf(couponBalance));
        params.put("csrf", userData.getBiliJct());
        params.put("csrf_token", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.LIVE_CREATE_ORDER, params, BiliApi.REFERER_LIVE);
        if (Request.code(response) == 0) {
            log.info("【使用B币券】: 成功将 {} B币卷兑换成 {} 金瓜子✔", couponBalance, couponBalance * 1000L);
        } else {
            log.warn("【使用B币券】: {}❌", Request.message(response));
        }
    }
}
