package top.srcrs.task.live;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;

/**
 * 送出即将过期的直播背包礼物。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class GiveGiftTask implements Task {

    private final UserData userData = UserData.getInstance();
    private final Config config = Config.getInstance();

    /** 距离过期不足一天就送出 */
    private static final long EXPIRE_THRESHOLD_SECONDS = 24 * 60 * 60L;

    @Override
    public void run() {
        if (!config.isGift()) {
            log.info("【送即将过期礼物】: 自定义配置不送出即将过期礼物✔");
            return;
        }

        JSONArray bag = bagList();
        if (bag == null || bag.isEmpty()) {
            log.info("【送即将过期礼物】: 礼物背包为空❌");
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        JSONObject room = null;
        boolean sentAny = false;

        for (int i = 0; i < bag.size(); i++) {
            JSONObject gift = bag.getJSONObject(i);
            if (gift == null || !isExpiring(gift, now)) {
                continue;
            }
            if (room == null) {
                room = pickRoom();
            }
            if (room == null) {
                log.warn("【送即将过期礼物】: 没有找到可用的直播间❌");
                return;
            }
            sentAny |= send(room, gift);
        }

        if (!sentAny) {
            log.info("【送即将过期礼物】: 当前无即将过期礼物❌");
        }
    }

    /**
     * 判断礼物是否即将过期。
     *
     * @param gift 背包里的一件礼物
     * @param now  当前时间戳（秒）
     * @return 距离过期不足一天返回 true；永久礼物的过期时间为 0，不算
     */
    private boolean isExpiring(JSONObject gift, long now) {
        long expireAt = gift.getLongValue("expire_at", 0L);
        return expireAt != 0 && expireAt - now < EXPIRE_THRESHOLD_SECONDS;
    }

    /**
     * 拉取直播背包。
     *
     * @return 礼物列表，失败时返回 null
     */
    private JSONArray bagList() {
        JSONObject response = Request.get(BiliApi.LIVE_BAG_LIST, new JSONObject(), BiliApi.REFERER_LIVE);
        if (Request.code(response) != 0) {
            log.warn("【送即将过期礼物】: 获取背包失败, {}❌", response.getString("message"));
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        return data == null ? null : data.getJSONArray("list");
    }

    /**
     * 送出一件礼物。
     * <p>
     * 老的 {@code gift/v2/live/bag_send} 已经下线，改用 {@code xlive/revenue/v1/gift/sendBag}。
     *
     * @param room 目标直播间，含 roomId 与 uid
     * @param gift 背包礼物
     * @return 是否送出成功
     */
    private boolean send(JSONObject room, JSONObject gift) {
        String roomId = room.getString("roomId");
        JSONObject params = new JSONObject();
        params.put("uid", userData.getMid());
        params.put("gift_id", gift.getString("gift_id"));
        params.put("ruid", room.getString("uid"));
        params.put("send_ruid", "0");
        params.put("gift_num", gift.getString("gift_num"));
        params.put("bag_id", gift.getString("bag_id"));
        params.put("platform", "pc");
        params.put("biz_code", "live");
        params.put("biz_id", roomId);
        params.put("rnd", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("storm_beat_id", "0");
        params.put("price", "0");
        params.put("csrf", userData.getBiliJct());
        params.put("csrf_token", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.LIVE_BAG_SEND, params,
                "https://live.bilibili.com/" + roomId);
        if (Request.code(response) == 0) {
            JSONObject data = response.getJSONObject("data");
            String name = data == null ? gift.getString("gift_name") : data.getString("gift_name");
            String num = data == null ? gift.getString("gift_num") : data.getString("gift_num");
            log.info("【送即将过期礼物】: 给直播间 - {} - {} - 数量: {}✔", roomId, name, num);
            return true;
        }
        log.warn("【送即将过期礼物】: 失败, 原因: {}❌", response.getString("message"));
        return false;
    }

    /**
     * 选一个直播间，优先用配置里指定的 UP 主。
     *
     * @return 含 {@code roomId} 与 {@code uid} 的对象；找不到时返回 null
     */
    private JSONObject pickRoom() {
        String upLive = StringUtil.trimToEmpty(config.getUpLive());
        if (StringUtil.isNotBlank(upLive)) {
            String roomId = roomIdOf(upLive);
            if (StringUtil.isNotBlank(roomId) && !"0".equals(roomId)) {
                log.info("【获取直播间】: 自定义up {} 的直播间", upLive);
                return room(roomId, upLive);
            }
            log.info("【获取直播间】: 自定义up {} 无直播间", upLive);
        }
        JSONObject random = randomRoom();
        if (random != null) {
            log.info("【获取直播间】: 随机直播间");
        }
        return random;
    }

    /**
     * 根据 uid 查直播间号。
     *
     * @param mid UP 主 uid
     * @return 房间号；没有直播间时返回 "0"
     */
    private String roomIdOf(String mid) {
        JSONObject params = new JSONObject();
        params.put("mid", mid);
        JSONObject response = Request.get(BiliApi.LIVE_ROOM_INFO_OLD, params, BiliApi.REFERER_LIVE);
        if (Request.code(response) != 0) {
            return "0";
        }
        JSONObject data = response.getJSONObject("data");
        return data == null ? "0" : data.getString("roomid");
    }

    /**
     * 从分区列表里随机取一个正在直播的房间。
     * <p>
     * 替代已经下线的 {@code relation/v1/AppWeb/getRecommendList}，
     * 而且这个接口一次就能同时拿到房间号和主播 uid。
     *
     * @return 房间信息；接口不可用时返回 null
     */
    private JSONObject randomRoom() {
        JSONObject params = new JSONObject();
        params.put("platform", "web");
        params.put("parent_area_id", "1");
        params.put("area_id", "0");
        params.put("sort_type", "online");
        params.put("page", "1");

        JSONObject response = Request.get(BiliApi.LIVE_ROOM_LIST, params, BiliApi.REFERER_LIVE);
        if (Request.code(response) != 0) {
            log.warn("⚠️获取直播间列表失败: {}", response.getString("message"));
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        JSONArray list = data == null ? null : data.getJSONArray("list");
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String roomId = item.getString("roomid");
            String uid = item.getString("uid");
            if (StringUtil.isNotBlank(roomId) && StringUtil.isNotBlank(uid)) {
                return room(roomId, uid);
            }
        }
        return null;
    }

    private JSONObject room(String roomId, String uid) {
        JSONObject json = new JSONObject();
        json.put("roomId", roomId);
        json.put("uid", uid);
        return json;
    }
}
