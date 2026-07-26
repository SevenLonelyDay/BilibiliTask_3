package top.srcrs.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.srcrs.domain.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置读取的测试。
 * <p>
 * SnakeYAML 从 1.x 升到 2.x 时对 JavaBean 的构造方式做过调整，
 * 这个用例保证升级依赖之后配置仍然能被正确映射进来。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class ReadConfigTest {

    @Test
    @DisplayName("能把打包进 jar 的 config.yml 映射成配置对象")
    void loadsBundledConfig() {
        assertTrue(ReadConfig.transformation("/config.yml"));

        Config config = Config.getInstance();
        assertNotNull(config.getCoin());
        assertTrue(config.getCoin() >= 0 && config.getCoin() <= 5);
        // 数字写法的配置项要映射成字符串，不能因为类型不匹配就整份配置失败
        assertEquals("1", config.getAutoBiCoin());
        assertEquals("0", config.getSelectLike());
        assertEquals("android", config.getPlatform());
        assertTrue(config.isManga());
        assertTrue(config.isGift());
        assertTrue(config.isS2c());
    }

    @Test
    @DisplayName("配置文件不存在时不抛异常，保留默认值")
    void missingConfigKeepsDefaults() {
        assertFalse(ReadConfig.transformation("/not-exists.yml"));
        assertNotNull(Config.getInstance().getPlatform());
    }
}
