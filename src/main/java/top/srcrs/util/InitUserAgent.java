package top.srcrs.util;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UserAgent 与设备标识。
 * <p>
 * 早期版本会随机拼出诸如 {@code Chrome/117.3.4821.55} 这种并不存在的版本号，风控侧反而更容易识别。
 * 这里改成从一小组真实存在的 UA 中挑一条，整个运行过程保持不变；也支持用环境变量
 * {@code BILI_UA} 覆盖成自己浏览器里的 UA。
 *
 * @author srcrs
 * @Time 2020-11-30
 */
public final class InitUserAgent {

    private InitUserAgent() {
    }

    /** 真实存在的桌面端 UA */
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.6 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36"
    );

    /**
     * 取本次运行使用的 UserAgent。
     *
     * @return UserAgent
     */
    public static String getOne() {
        String custom = System.getenv("BILI_UA");
        if (StringUtil.isNotBlank(custom)) {
            return custom;
        }
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }

    /**
     * 本地生成一个 buvid3，供 finger 接口不可用时兜底。
     *
     * @return 形如 {@code XXXXXXXX-....-12345infoc} 的设备标识
     */
    public static String randomBuvid() {
        String uuid = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        int suffix = ThreadLocalRandom.current().nextInt(10000, 100000);
        return uuid + suffix + "infoc";
    }
}
