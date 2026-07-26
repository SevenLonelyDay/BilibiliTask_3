package top.srcrs.task.bigvip;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.UserData;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;

import java.util.Map;

/**
 * 领取大会员每月权益。
 * <p>
 * 老实现把领取时间写死成每月 1 号，错过当天就得等下个月。现在改成先查
 * {@code x/vip/privilege/my}，哪些权益还没领就领哪些，任何一天跑都能补上。
 *
 * @author srcrs
 * @Time 2020-10-19
 */
@Slf4j
public class CollectVipGift implements Task {

    private final UserData userData = UserData.getInstance();

    /** 大会员生效中 */
    private static final String VIP_ACTIVE = "1";
    /** 权益状态：未领取 */
    private static final int NOT_RECEIVED = 0;

    /** 权益类型对应的名字，仅用于日志 */
    private static final Map<Integer, String> PRIVILEGE_NAMES = Map.of(
            1, "B币券",
            2, "会员购优惠券",
            3, "漫画福利券",
            4, "漫画商城优惠券",
            5, "观影券",
            6, "会员体验卡",
            7, "大会员权益"
    );

    @Override
    public void run() {
        if (!VIP_ACTIVE.equals(userData.getVipStatus())) {
            log.info("【大会员领取福利】: 当前不是大会员,无法领取❌");
            return;
        }

        JSONArray privileges = myPrivileges();
        if (privileges == null) {
            log.warn("【大会员领取福利】: 权益列表获取失败❌");
            return;
        }

        int received = 0;
        int pending = 0;
        for (int i = 0; i < privileges.size(); i++) {
            JSONObject privilege = privileges.getJSONObject(i);
            if (privilege == null || privilege.getIntValue("state", -1) != NOT_RECEIVED) {
                continue;
            }
            pending++;
            if (receive(privilege.getIntValue("type", 0))) {
                received++;
            }
        }

        if (pending == 0) {
            log.info("【大会员领取福利】: 本月权益都已经领过了✔");
        } else {
            log.info("【大会员领取福利】: 待领取 {} 项, 成功领取 {} 项", pending, received);
        }
    }

    /**
     * 查询本月的大会员权益列表。
     *
     * @return 权益列表；接口失败时返回 null
     */
    private JSONArray myPrivileges() {
        JSONObject response = Request.get(BiliApi.VIP_PRIVILEGE_MY, new JSONObject(), BiliApi.REFERER_VIP);
        if (Request.code(response) != 0) {
            log.debug("权益列表接口返回: {} - {}", response.getString("code"), Request.message(response));
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        return data == null ? null : data.getJSONArray("list");
    }

    /**
     * 领取一项权益。
     *
     * @param type 权益类型
     * @return 是否领取成功
     */
    private boolean receive(int type) {
        String name = PRIVILEGE_NAMES.getOrDefault(type, "权益" + type);
        JSONObject params = new JSONObject();
        params.put("type", String.valueOf(type));
        params.put("csrf", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.VIP_PRIVILEGE_RECEIVE, params, BiliApi.REFERER_VIP);
        if (Request.code(response) == 0) {
            log.info("【领取{}】: 成功✔", name);
            return true;
        }
        log.warn("【领取{}】: 失败, 原因: {}❌", name, Request.message(response));
        return false;
    }
}
