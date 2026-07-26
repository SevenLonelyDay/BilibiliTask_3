package top.srcrs.util;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import top.srcrs.domain.Config;

import java.io.InputStream;

/**
 * 读取 yml 配置。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public final class ReadConfig {

    private ReadConfig() {
    }

    /**
     * 把 yml 里的配置映射到 {@link Config}。
     * <p>
     * 读不到就保留 {@link Config} 里的默认值，不会中断运行。
     *
     * @param file classpath 下的配置文件路径
     * @return 是否读取成功
     */
    public static boolean transformation(String file) {
        try (InputStream in = ReadConfig.class.getResourceAsStream(file)) {
            if (in == null) {
                log.error("💔找不到配置文件 {}，将使用默认配置", file);
                return false;
            }
            new Yaml().loadAs(in, Config.class);
            log.info("✅配置文件读取完成");
            return true;
        } catch (Exception e) {
            log.error("💔配置文件解析失败，将使用默认配置: ", e);
            return false;
        }
    }
}
