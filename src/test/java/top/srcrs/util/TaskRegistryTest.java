package top.srcrs.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.srcrs.Task;
import top.srcrs.task.bigvip.BiCoinApply;
import top.srcrs.task.bigvip.CollectVipGift;
import top.srcrs.task.live.BiLiveTask;
import top.srcrs.task.live.GiveGiftTask;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务清单的测试。
 *
 * @author Ali
 * @Time 2020-12-05
 */
class TaskRegistryTest {

    @Test
    @DisplayName("登记的类都实现了 Task 且能被实例化")
    void allTasksAreInstantiable() {
        List<Class<? extends Task>> classes = TaskRegistry.tasks();
        assertFalse(classes.isEmpty());
        for (Class<? extends Task> clazz : classes) {
            assertTrue(Task.class.isAssignableFrom(clazz), clazz.getName() + " 没有实现 Task");
        }
        // 有构造不出来的类时 instantiate 会静默跳过，数量对不上就说明出问题了
        assertEquals(classes.size(), TaskRegistry.instantiate().size());
    }

    @Test
    @DisplayName("执行顺序满足任务之间的依赖关系")
    void executionOrderRespectsDependencies() {
        List<Class<? extends Task>> classes = TaskRegistry.tasks();
        // 先领到 B 币券，才谈得上月底把它花掉
        assertTrue(classes.indexOf(CollectVipGift.class) < classes.indexOf(BiCoinApply.class));
        // 直播签到的礼物到账之后，送礼物任务才能在背包里看到它
        assertTrue(classes.indexOf(BiLiveTask.class) < classes.indexOf(GiveGiftTask.class));
    }
}
