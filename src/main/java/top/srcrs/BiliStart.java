package top.srcrs;

import lombok.extern.slf4j.Slf4j;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.util.Account;
import top.srcrs.util.InitUserAgent;
import top.srcrs.util.ReadConfig;
import top.srcrs.util.Request;
import top.srcrs.util.SendDingTalk;
import top.srcrs.util.SendPushPlus;
import top.srcrs.util.SendServerChan;
import top.srcrs.util.SendTelegram;
import top.srcrs.util.StringUtil;
import top.srcrs.util.TaskRegistry;

import java.util.List;

/**
 * 启动类，程序运行开始的地方。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public final class BiliStart {

    private BiliStart() {
    }

    private static final UserData USER_DATA = UserData.getInstance();
    private static final Config CONFIG = Config.getInstance();

    /** 最高等级，到了这一级就不再估算升级时间 */
    private static final String MAX_LEVEL = "6";
    /** 默认的整体超时时间，可用环境变量 BILI_TIMEOUT_MINUTES 调整 */
    private static final int DEFAULT_TIMEOUT_MINUTES = 10;

    /** 任务抛出未捕获异常的次数 */
    private static int failedTasks = 0;

    public static void main(String... args) {
        Thread watchdog = startWatchdog();
        int exitCode = 0;
        try {
            exitCode = runAll();
        } catch (RuntimeException e) {
            log.error("💔程序运行出现未预期的异常: ", e);
            exitCode = 1;
        } finally {
            watchdog.interrupt();
            notifyUser();
        }
        System.exit(exitCode);
    }

    /**
     * 完整跑一遍。
     *
     * @return 进程退出码
     */
    private static int runAll() {
        if (!loadCookie()) {
            log.error("💔请在 Github Secrets 中添加你的 Cookie 信息 (BILI_JCT / SESSDATA / DEDEUSERID)");
            return 1;
        }

        ReadConfig.transformation("/config.yml");
        Request.setUserAgent(InitUserAgent.getOne());

        if (!Account.refresh()) {
            return 1;
        }

        log.info("【用户名】: {}", StringUtil.hideString(USER_DATA.getUname(), 1, 1, '*'));
        log.info("【硬币】: {}", USER_DATA.getMoney());
        log.info("【经验】: {}", USER_DATA.getCurrentExp());

        runTasks();
        logLevelEstimate();

        log.info("本次任务运行完毕。");
        if (Request.transportErrors() > 0) {
            log.warn("⚠️本次运行有 {} 次请求始终没能成功，部分任务可能没做完", Request.transportErrors());
        }
        if (failedTasks > 0) {
            log.error("💔有 {} 个任务执行异常，请检查上面的日志", failedTasks);
            return 1;
        }
        return 0;
    }

    /**
     * 依次执行所有任务。单个任务出错不影响后面的任务。
     */
    private static void runTasks() {
        List<Task> tasks = TaskRegistry.instantiate();
        for (Task task : tasks) {
            String name = task.getClass().getSimpleName();
            try {
                log.debug("开始执行任务: {}", name);
                task.run();
            } catch (Exception e) {
                log.error("💔任务执行失败 [{}] : ", name, e);
                failedTasks++;
            }
        }
    }

    /**
     * 从环境变量读取 Cookie。
     *
     * @return 三个变量都存在返回 true
     */
    private static boolean loadCookie() {
        String biliJct = System.getenv("BILI_JCT");
        String sessData = System.getenv("SESSDATA");
        String dedeUserId = System.getenv("DEDEUSERID");
        if (StringUtil.isAnyBlank(biliJct, sessData, dedeUserId)) {
            return false;
        }
        USER_DATA.setCookie(StringUtil.trimToEmpty(biliJct),
                StringUtil.trimToEmpty(sessData),
                StringUtil.trimToEmpty(dedeUserId));
        return true;
    }

    /**
     * 兜底的超时保护，卡死时不至于把 Actions 的额度耗光。
     *
     * @return 看门狗线程，正常结束时需要中断掉
     */
    private static Thread startWatchdog() {
        int minutes = timeoutMinutes();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(minutes * 60_000L);
                log.error("💔程序运行超时({}分钟)，强制退出", minutes);
                System.exit(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bili-task-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }

    private static int timeoutMinutes() {
        String configured = System.getenv("BILI_TIMEOUT_MINUTES");
        if (StringUtil.isNotBlank(configured)) {
            try {
                int minutes = Integer.parseInt(configured.trim());
                if (minutes > 0) {
                    return minutes;
                }
            } catch (NumberFormatException e) {
                log.warn("⚠️BILI_TIMEOUT_MINUTES 不是数字，按默认 {} 分钟处理", DEFAULT_TIMEOUT_MINUTES);
            }
        }
        return DEFAULT_TIMEOUT_MINUTES;
    }

    /**
     * 输出升级预计。
     */
    private static void logLevelEstimate() {
        String level = USER_DATA.getCurrentLevel();
        if (MAX_LEVEL.equals(level)) {
            log.info("【升级预计】: 当前等级为: Lv{} ,已经是最高等级", MAX_LEVEL);
            log.info("【温馨提示】: 可在配置文件中关闭每日投币操作");
            return;
        }
        int days = daysToNextLevel();
        if (days < 0) {
            log.info("【升级预计】: 当前等级为: Lv{}", level);
            return;
        }
        log.info("【升级预计】: 当前等级为: Lv{} ,预计升级到下一级还需要: {} 天", level, days);
    }

    /**
     * 估算距离升到下一级还要几天。
     * <p>
     * 为了少打几次接口，估算里认为每天都能拿满登录和观看分享的经验，会有一天左右的误差。
     *
     * @return 天数；数据不全时返回 -1
     */
    private static int daysToNextLevel() {
        Integer currentExpValue = USER_DATA.getCurrentExp();
        if (currentExpValue == null) {
            return -1;
        }
        int nextExp;
        try {
            nextExp = Integer.parseInt(StringUtil.trimToEmpty(USER_DATA.getNextExp()));
        } catch (NumberFormatException e) {
            // 满级时这个字段是 "--"
            return -1;
        }

        int currentExp = currentExpValue;
        int wallet = USER_DATA.getMoney() == null ? 0 : USER_DATA.getMoney().intValue();
        int planned = CONFIG.getCoin() == null ? 0 : Math.max(CONFIG.getCoin(), 0);
        int coin = Math.min(wallet, planned);

        int days = 0;
        while (currentExp < nextExp && days < 100_000) {
            days++;
            wallet += 1;
            // 每日登录 5 + 观看 5 + 分享 5，投一个币 10
            currentExp += 15 + coin * 10;
            wallet -= coin;
            coin = Math.min(wallet, planned);
        }
        return days;
    }

    /**
     * 把运行结果推送出去。任意一个渠道出错都不影响其它渠道。
     */
    private static void notifyUser() {
        if (StringUtil.isBlank(System.getenv("SENDKEY")) && StringUtil.isNotBlank(System.getenv("SCKEY"))) {
            log.warn("⚠️SCKEY 对应的旧版 Server 酱 (sc.ftqq.com) 早已下线，本次按 SENDKEY 处理，"
                    + "建议在 Secrets 里改名为 SENDKEY 并换成 Server 酱 Turbo 的 SendKey");
        }
        String sendKey = firstNotBlank(System.getenv("SENDKEY"), System.getenv("SCKEY"));
        if (StringUtil.isNotBlank(sendKey)) {
            SendServerChan.send(sendKey);
        }
        if (StringUtil.isNotBlank(System.getenv("PUSHPLUSTK"))) {
            SendPushPlus.send(System.getenv("PUSHPLUSTK"));
        }
        if (StringUtil.isNotBlank(System.getenv("DINGTALK"))) {
            SendDingTalk.send(System.getenv("DINGTALK"));
        }
        if (StringUtil.isNotBlank(System.getenv("TELEGRAM_BOT_TOKEN"))
                && StringUtil.isNotBlank(System.getenv("TELEGRAM_CHAT_ID"))) {
            SendTelegram.send(System.getenv("TELEGRAM_BOT_TOKEN"), System.getenv("TELEGRAM_CHAT_ID"));
        }
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
