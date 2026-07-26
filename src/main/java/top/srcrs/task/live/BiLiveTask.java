package top.srcrs.task.live;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.util.BiliApi;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 直播签到。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class BiLiveTask implements Task {

    /** 重复签到的业务码 */
    private static final int ALREADY_SIGNED = 1011040;

    @Override
    public void run() {
        JSONObject response = Request.get(BiliApi.LIVE_SIGN, new JSONObject(), BiliApi.REFERER_LIVE_LINK);
        int code = Request.code(response);

        if (code == 0) {
            JSONObject data = response.getJSONObject("data");
            String text = data == null ? "" : StringUtil.trimToEmpty(data.getString("text"));
            String special = data == null ? "" : StringUtil.trimToEmpty(data.getString("specialText"));
            log.info("【直播签到】: 获得{} ,{}✔", text, special);
            // 稍等一会儿，签到礼物到账之后 GiveGiftTask 才能在背包里看到它
            sleep();
            return;
        }
        if (code == ALREADY_SIGNED) {
            log.info("【直播签到】: 今日已经签到过了❌");
            return;
        }
        log.warn("【直播签到】: {}({})❌", response.getString("message"), code);
    }

    private void sleep() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(3000, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
