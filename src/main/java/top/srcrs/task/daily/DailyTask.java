package top.srcrs.task.daily;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.UserData;
import top.srcrs.domain.VideoInfo;
import top.srcrs.util.BiliApi;
import top.srcrs.util.DailyReward;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;
import top.srcrs.util.VideoSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 每日的观看视频与分享视频任务。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class DailyTask implements Task {

    private final UserData userData = UserData.getInstance();

    /** 观看时长的下限，太短不会被判定为有效观看 */
    private static final int MIN_WATCH_SECONDS = 30;
    /** 观看时长的上限，没必要报得太久 */
    private static final int MAX_WATCH_SECONDS = 90;

    @Override
    public void run() {
        JSONObject reward = DailyReward.get();
        boolean watched = reward.getBooleanValue("watch");
        boolean shared = reward.getBooleanValue("share");

        if (watched && shared) {
            log.info("【模拟观看视频】: 今日已经观看过视频❌");
            log.info("【分享视频】: 今日已经分享过视频❌");
            return;
        }

        VideoInfo video = pickVideo();
        if (video == null) {
            log.warn("【每日任务】: 没有取到可用视频，观看与分享任务本次跳过❌");
            return;
        }
        log.info("【选中视频】: av{} - {}", video.getAid(), video.getTitle());

        if (watched) {
            log.info("【模拟观看视频】: 今日已经观看过视频❌");
        } else {
            log.info("【模拟观看视频】: {}", watch(video) ? "成功✔" : "失败❌");
        }

        if (shared) {
            log.info("【分享视频】: 今日已经分享过视频❌");
        } else {
            log.info("【分享视频】: {}", share(video));
        }
    }

    /**
     * 挑一条能用来观看和分享的视频。
     * <p>
     * 老实现固定取列表里的第 6 条，接口一旦少返回几条就直接数组越界。
     *
     * @return 视频信息；一条都拿不到时返回 null
     */
    private VideoInfo pickVideo() {
        List<VideoInfo> videos = VideoSource.candidates(10);
        if (videos.isEmpty()) {
            return null;
        }
        // 从候选里随机挑，避免所有账号每天都盯着同一条视频刷
        int start = ThreadLocalRandom.current().nextInt(videos.size());
        for (int i = 0; i < videos.size(); i++) {
            VideoInfo video = videos.get((start + i) % videos.size());
            if (VideoSource.fillPlayInfo(video)) {
                return video;
            }
        }
        return null;
    }

    /**
     * 模拟观看：先发视频心跳，再补一次播放进度上报。
     * <p>
     * 现在的观看经验主要由心跳接口发放，老的 {@code x/v2/history/report} 单独调用已经不一定算数，
     * 但它仍然有效，保留下来当兜底。
     *
     * @param video 视频信息
     * @return 是否有任意一个接口上报成功
     */
    private boolean watch(VideoInfo video) {
        int duration = video.getDuration() > 0 ? video.getDuration() : MAX_WATCH_SECONDS;
        int watchSeconds = Math.max(1, Math.min(duration, randomWatchSeconds(duration)));

        boolean heartbeatOk = heartbeat(video, duration, watchSeconds);
        boolean reportOk = report(video, watchSeconds);
        log.debug("观看上报: 心跳={}, 进度上报={}, 时长={}s", heartbeatOk, reportOk, watchSeconds);
        return heartbeatOk || reportOk;
    }

    private int randomWatchSeconds(int duration) {
        if (duration <= MIN_WATCH_SECONDS) {
            return duration;
        }
        int upper = Math.min(duration, MAX_WATCH_SECONDS);
        return ThreadLocalRandom.current().nextInt(MIN_WATCH_SECONDS, upper + 1);
    }

    /**
     * 视频心跳。
     *
     * @param video        视频信息
     * @param duration     视频总时长（秒）
     * @param watchSeconds 本次观看时长（秒）
     * @return 是否上报成功
     */
    private boolean heartbeat(VideoInfo video, int duration, int watchSeconds) {
        long now = System.currentTimeMillis() / 1000;
        JSONObject params = new JSONObject();
        params.put("start_ts", String.valueOf(now - watchSeconds));
        params.put("mid", userData.getMid());
        params.put("aid", video.getAid());
        params.put("cid", video.getCid());
        params.put("type", "3");
        params.put("sub_type", "0");
        params.put("dt", "2");
        params.put("play_type", "1");
        params.put("realtime", String.valueOf(watchSeconds));
        params.put("played_time", String.valueOf(watchSeconds));
        params.put("real_played_time", String.valueOf(watchSeconds));
        params.put("refer_url", BiliApi.REFERER_MAIN);
        params.put("quality", "80");
        params.put("video_duration", String.valueOf(duration));
        params.put("last_play_progress_time", String.valueOf(watchSeconds));
        params.put("max_play_progress_time", String.valueOf(watchSeconds));
        params.put("outer", "0");
        params.put("spmid", "333.788.0.0");
        params.put("from_spmid", "333.1007.tianma.1-1-1.click");
        params.put("session", UUID.randomUUID().toString().replace("-", ""));
        params.put("csrf", userData.getBiliJct());

        // aid 与 played_time 除了放在表单里，还要跟在地址后面，web 端就是这么发的
        String url = BiliApi.CLICK_HEARTBEAT
                + "?aid=" + video.getAid()
                + "&played_time=" + watchSeconds;
        JSONObject response = Request.post(url, params,
                BiliApi.videoPage(video.getBvid(), video.getAid()));
        return Request.code(response) == 0;
    }

    /**
     * 上报播放进度。
     *
     * @param video        视频信息
     * @param watchSeconds 观看到的秒数
     * @return 是否上报成功
     */
    private boolean report(VideoInfo video, int watchSeconds) {
        JSONObject params = new JSONObject();
        params.put("aid", video.getAid());
        params.put("cid", video.getCid());
        params.put("progres", String.valueOf(watchSeconds));
        params.put("csrf", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.HISTORY_REPORT, params,
                BiliApi.videoPage(video.getBvid(), video.getAid()));
        return Request.code(response) == 0;
    }

    /**
     * 分享视频。
     *
     * @param video 视频信息
     * @return 展示用的结果文案
     */
    private String share(VideoInfo video) {
        JSONObject params = new JSONObject();
        // bvid 和 aid 只发一个，同时带上时接口会以 bvid 为准，没必要多传
        if (StringUtil.isNotBlank(video.getBvid())) {
            params.put("bvid", video.getBvid());
        } else {
            params.put("aid", video.getAid());
        }
        params.put("csrf", userData.getBiliJct());

        JSONObject response = Request.post(BiliApi.SHARE_ADD, params,
                BiliApi.videoPage(video.getBvid(), video.getAid()));
        int code = Request.code(response);
        if (code == 0) {
            return "成功✔";
        }
        // 71000 是重复分享，说明今天其实已经分享过了
        if (code == 71000) {
            return "今日已经分享过视频❌";
        }
        return response.getString("message") + "❌";
    }
}
