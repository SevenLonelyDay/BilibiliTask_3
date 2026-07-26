package top.srcrs.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.domain.UserData;
import top.srcrs.domain.VideoInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频来源。
 * <p>
 * 观看、分享、投币都需要先拿到几条视频。这里集中处理各个来源的解析，顺带替换掉两个已经下线的接口：
 * <ul>
 *   <li>{@code x/web-interface/dynamic/region}（分区最新）→ 综合热门 + 首页推荐；</li>
 *   <li>{@code api.vc.bilibili.com/dynamic_svr/...}（关注动态）→ {@code x/polymer/web-dynamic/v1/feed/all}。</li>
 * </ul>
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Slf4j
public final class VideoSource {

    private VideoSource() {
    }

    private static final UserData USER_DATA = UserData.getInstance();

    /**
     * 取一批可用的视频，优先首页推荐，不足时用综合热门补齐。
     *
     * @param limit 需要的数量
     * @return 视频列表，可能少于 limit
     */
    public static List<VideoInfo> candidates(int limit) {
        Map<String, VideoInfo> merged = new LinkedHashMap<>();
        for (VideoInfo video : recommend(limit)) {
            merged.putIfAbsent(video.getAid(), video);
        }
        if (merged.size() < limit) {
            for (VideoInfo video : popular(limit)) {
                merged.putIfAbsent(video.getAid(), video);
            }
        }
        if (merged.size() < limit) {
            // 排行榜既不要登录也不要签名，前两路都塌了它多半还在
            for (VideoInfo video : ranking(limit)) {
                merged.putIfAbsent(video.getAid(), video);
            }
        }
        if (merged.isEmpty()) {
            log.warn("⚠️没有取到任何可用视频，推荐、热门、排行榜接口可能都不可用");
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 全站排行榜。
     *
     * @param limit 需要的数量
     * @return 视频列表
     */
    public static List<VideoInfo> ranking(int limit) {
        JSONObject params = new JSONObject();
        params.put("rid", "0");
        params.put("type", "all");

        JSONObject response = Request.get(BiliApi.RANKING_V2, params);
        if (Request.code(response) != 0) {
            log.debug("排行榜接口不可用: {} - {}", response.getString("code"), Request.message(response));
            return new ArrayList<>();
        }
        JSONObject data = response.getJSONObject("data");
        return parseArchives(data == null ? null : data.getJSONArray("list"), limit);
    }

    /**
     * 首页推荐视频，需要 WBI 签名。
     *
     * @param limit 需要的数量
     * @return 视频列表
     */
    public static List<VideoInfo> recommend(int limit) {
        JSONObject params = new JSONObject();
        params.put("ps", String.valueOf(Math.max(limit, 12)));
        params.put("fresh_type", "3");
        params.put("fresh_idx", "1");
        params.put("fresh_idx_1h", "1");
        params.put("fetch_row", "1");
        params.put("brush", "0");
        params.put("homepage_ver", "1");
        params.put("version", "1");

        JSONObject response = Request.getWbi(BiliApi.RCMD_WBI, params);
        List<VideoInfo> videos = new ArrayList<>();
        if (Request.code(response) != 0) {
            log.debug("首页推荐接口不可用: {} - {}", response.getString("code"), response.getString("message"));
            return videos;
        }
        JSONObject data = response.getJSONObject("data");
        JSONArray items = data == null ? null : data.getJSONArray("item");
        if (items == null) {
            return videos;
        }
        for (int i = 0; i < items.size() && videos.size() < limit; i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            // 推荐流里混着直播、广告等卡片，只保留普通视频
            String goTo = item.getString("goto");
            if (goTo != null && !"av".equals(goTo)) {
                continue;
            }
            VideoInfo video = new VideoInfo();
            video.setAid(item.getString("id"));
            video.setBvid(item.getString("bvid"));
            video.setCid(item.getString("cid"));
            video.setTitle(item.getString("title"));
            video.setDuration(item.getIntValue("duration", 0));
            JSONObject owner = item.getJSONObject("owner");
            video.setMid(owner == null ? null : owner.getString("mid"));
            if (video.isUsable()) {
                videos.add(video);
            }
        }
        return videos;
    }

    /**
     * 综合热门视频。
     *
     * @param limit 需要的数量
     * @return 视频列表
     */
    public static List<VideoInfo> popular(int limit) {
        JSONObject params = new JSONObject();
        params.put("ps", String.valueOf(Math.max(limit, 20)));
        params.put("pn", "1");

        JSONObject response = Request.get(BiliApi.POPULAR, params);
        List<VideoInfo> videos = new ArrayList<>();
        if (Request.code(response) != 0) {
            log.debug("热门接口不可用: {} - {}", response.getString("code"), response.getString("message"));
            return videos;
        }
        JSONObject data = response.getJSONObject("data");
        return parseArchives(data == null ? null : data.getJSONArray("list"), limit);
    }

    /**
     * 解析常规的稿件列表。热门和排行榜的条目结构是一样的。
     *
     * @param list  稿件数组，允许为 null
     * @param limit 需要的数量
     * @return 视频列表
     */
    private static List<VideoInfo> parseArchives(JSONArray list, int limit) {
        List<VideoInfo> videos = new ArrayList<>();
        if (list == null) {
            return videos;
        }
        for (int i = 0; i < list.size() && videos.size() < limit; i++) {
            JSONObject item = list.getJSONObject(i);
            if (item == null) {
                continue;
            }
            VideoInfo video = new VideoInfo();
            video.setAid(item.getString("aid"));
            video.setBvid(item.getString("bvid"));
            video.setCid(item.getString("cid"));
            video.setTitle(item.getString("title"));
            video.setDuration(item.getIntValue("duration", 0));
            JSONObject owner = item.getJSONObject("owner");
            video.setMid(owner == null ? null : owner.getString("mid"));
            if (video.isUsable()) {
                videos.add(video);
            }
        }
        return videos;
    }

    /**
     * 当前账号关注列表里的最新视频动态。
     *
     * @param limit 需要的数量
     * @return 视频列表
     */
    public static List<VideoInfo> following(int limit) {
        JSONObject params = new JSONObject();
        params.put("type", "video");
        params.put("page", "1");
        params.put("timezone_offset", "-480");
        params.put("platform", "web");

        JSONObject response = Request.get(BiliApi.DYNAMIC_FEED_ALL, params, "https://t.bilibili.com/");
        List<VideoInfo> videos = new ArrayList<>();
        if (Request.code(response) != 0) {
            log.debug("关注动态接口不可用: {} - {}", response.getString("code"), response.getString("message"));
            return videos;
        }
        JSONObject data = response.getJSONObject("data");
        JSONArray items = data == null ? null : data.getJSONArray("items");
        if (items == null) {
            return videos;
        }
        for (int i = 0; i < items.size() && videos.size() < limit; i++) {
            VideoInfo video = parseDynamicItem(items.getJSONObject(i));
            if (video != null) {
                videos.add(video);
            }
        }
        return videos;
    }

    /**
     * 解析一条动态，只认视频投稿。
     *
     * @param item 动态条目
     * @return 视频信息；不是视频投稿时返回 null
     */
    private static VideoInfo parseDynamicItem(JSONObject item) {
        if (item == null || !"DYNAMIC_TYPE_AV".equals(item.getString("type"))) {
            return null;
        }
        JSONObject modules = item.getJSONObject("modules");
        if (modules == null) {
            return null;
        }
        JSONObject dynamic = modules.getJSONObject("module_dynamic");
        JSONObject major = dynamic == null ? null : dynamic.getJSONObject("major");
        JSONObject archive = major == null ? null : major.getJSONObject("archive");
        if (archive == null) {
            return null;
        }
        VideoInfo video = new VideoInfo();
        video.setAid(archive.getString("aid"));
        video.setBvid(archive.getString("bvid"));
        video.setTitle(archive.getString("title"));
        JSONObject author = modules.getJSONObject("module_author");
        video.setMid(author == null ? null : author.getString("mid"));
        return video.isUsable() ? video : null;
    }

    /**
     * 某个 UP 主的最新投稿。
     * <p>
     * 老的 {@code x/space/arc/search} 已经要求 WBI 签名，这里走 {@code x/space/wbi/arc/search}。
     *
     * @param mid   UP 主 uid
     * @param limit 需要的数量
     * @return 视频列表
     */
    public static List<VideoInfo> space(String mid, int limit) {
        JSONObject params = new JSONObject();
        params.put("mid", mid);
        params.put("ps", "30");
        params.put("pn", "1");
        params.put("order", "pubdate");
        params.put("index", "1");
        params.put("platform", "web");
        params.put("web_location", "1550101");

        JSONObject response = Request.getWbi(BiliApi.SPACE_ARC_SEARCH_WBI, params,
                "https://space.bilibili.com/" + mid);
        List<VideoInfo> videos = new ArrayList<>();
        if (Request.code(response) != 0) {
            log.debug("UP 主 {} 投稿列表不可用: {} - {}", mid,
                    response.getString("code"), response.getString("message"));
            return videos;
        }
        JSONObject data = response.getJSONObject("data");
        JSONObject list = data == null ? null : data.getJSONObject("list");
        JSONArray vlist = list == null ? null : list.getJSONArray("vlist");
        if (vlist == null) {
            return videos;
        }
        for (int i = 0; i < vlist.size() && videos.size() < limit; i++) {
            JSONObject item = vlist.getJSONObject(i);
            if (item == null) {
                continue;
            }
            VideoInfo video = new VideoInfo();
            video.setAid(item.getString("aid"));
            video.setBvid(item.getString("bvid"));
            video.setMid(item.getString("mid"));
            video.setTitle(item.getString("title"));
            if (video.isUsable()) {
                videos.add(video);
            }
        }
        return videos;
    }

    /**
     * 补齐视频的 cid 与时长。
     * <p>
     * 动态、投稿列表这些来源不带 cid，观看上报时必须要有。
     *
     * @param video 视频信息，会被就地补充
     * @return 是否补齐成功
     */
    public static boolean fillPlayInfo(VideoInfo video) {
        if (video == null || !video.isUsable()) {
            return false;
        }
        if (StringUtil.isNotBlank(video.getCid()) && video.getDuration() > 0) {
            return true;
        }
        JSONObject params = new JSONObject();
        params.put("aid", video.getAid());
        JSONObject response = Request.get(BiliApi.PAGELIST, params);
        if (Request.code(response) != 0) {
            return false;
        }
        JSONArray pages = response.getJSONArray("data");
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        JSONObject first = pages.getJSONObject(0);
        video.setCid(first.getString("cid"));
        video.setDuration(first.getIntValue("duration", 0));
        return StringUtil.isNotBlank(video.getCid());
    }

    /**
     * 判断这条视频是否值得投币：不是自己的稿件，且当前投币数为 0。
     *
     * @param video 视频
     * @return 可以投币返回 true
     */
    public static boolean coinable(VideoInfo video) {
        if (video == null || !video.isUsable()) {
            return false;
        }
        String mid = USER_DATA.getMid();
        if (mid != null && mid.equals(video.getMid())) {
            return false;
        }
        JSONObject params = new JSONObject();
        params.put("aid", video.getAid());
        JSONObject response = Request.get(BiliApi.ARCHIVE_COINS, params);
        if (Request.code(response) != 0) {
            // 查不到就当作没投过，投币接口自己也会拦重复投币
            return true;
        }
        JSONObject data = response.getJSONObject("data");
        return data == null || data.getIntValue("multiply", 0) == 0;
    }
}
