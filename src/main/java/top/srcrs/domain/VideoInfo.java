package top.srcrs.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条视频的最小信息集合。
 * <p>
 * 不同来源（热门、推荐、动态、UP 主投稿）返回的字段结构差别很大，统一收敛成这个对象，
 * 任务代码就不用各写一套解析了。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoInfo {

    /** 视频 aid */
    private String aid;
    /** 视频 bvid，拼 Referer 时用 */
    private String bvid;
    /** 分 P 的 cid，部分来源不返回，需要时再单独查 */
    private String cid;
    /** UP 主 uid */
    private String mid;
    /** 视频标题 */
    private String title;
    /** 视频时长（秒），部分来源不返回时为 0 */
    private int duration;

    /**
     * 判断信息是否够用。
     *
     * @return aid 存在即认为可用
     */
    public boolean isUsable() {
        return aid != null && !aid.isEmpty() && !"0".equals(aid);
    }
}
