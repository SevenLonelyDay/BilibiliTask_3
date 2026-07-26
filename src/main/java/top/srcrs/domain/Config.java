package top.srcrs.domain;

import java.util.List;

/**
 * 项目的配置。
 * <p>
 * 字段是静态的，实例方法只是给 SnakeYAML 当 JavaBean 用：它会 new 一个临时对象再调 setter，
 * 值最终落到静态字段上，任何地方通过 {@link #getInstance()} 都能读到同一份配置。
 * <p>
 * 每个字段都给了保守的默认值，配置文件读不出来时程序仍能跑，只是不做有副作用的操作。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
public class Config {

    private static final Config CONFIG = new Config();

    public static Config getInstance() {
        return CONFIG;
    }

    private Config() {
    }

    /** 每日投币数量 [0,5] */
    private static Integer coin = 0;
    /** 是否送出即将过期的礼物 */
    private static boolean gift = false;
    /** 是否把银瓜子兑换成硬币 */
    private static boolean s2c = false;
    /** 月底如何使用 B 币券 [0 不使用, 1 给自己充电, 2 兑换金瓜子] */
    private static String autoBiCoin = "0";
    /** 漫画签到使用的设备标识 [android, ios] */
    private static String platform = "android";
    /** 优先投币的 up 主 uid 列表 */
    private static List<String> upList;
    /** 是否执行漫画签到 */
    private static boolean manga = false;
    /** 优先把即将过期的礼物送给这个 up 的直播间 */
    private static String upLive;
    /** 投币时是否顺带点赞 [0 否, 1 是] */
    private static String selectLike = "0";

    public String getSelectLike() {
        return selectLike;
    }

    public void setSelectLike(String selectLike) {
        Config.selectLike = selectLike;
    }

    public String getUpLive() {
        return upLive;
    }

    public void setUpLive(String upLive) {
        Config.upLive = upLive;
    }

    public boolean isManga() {
        return manga;
    }

    public void setManga(boolean manga) {
        Config.manga = manga;
    }

    public List<String> getUpList() {
        return upList;
    }

    public void setUpList(List<String> upList) {
        Config.upList = upList;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        Config.platform = platform;
    }

    public String getAutoBiCoin() {
        return autoBiCoin;
    }

    public void setAutoBiCoin(String autoBiCoin) {
        Config.autoBiCoin = autoBiCoin;
    }

    public Integer getCoin() {
        return coin;
    }

    public void setCoin(Integer coin) {
        Config.coin = coin;
    }

    public boolean isGift() {
        return gift;
    }

    public void setGift(boolean gift) {
        Config.gift = gift;
    }

    public boolean isS2c() {
        return s2c;
    }

    public void setS2c(boolean s2c) {
        Config.s2c = s2c;
    }
}
