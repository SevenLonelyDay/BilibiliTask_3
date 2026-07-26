package top.srcrs.util;

import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.task.bigvip.BiCoinApply;
import top.srcrs.task.bigvip.CollectVipGift;
import top.srcrs.task.daily.DailyTask;
import top.srcrs.task.daily.ThrowCoinTask;
import top.srcrs.task.live.BiLiveTask;
import top.srcrs.task.live.GiveGiftTask;
import top.srcrs.task.live.Silver2CoinTask;
import top.srcrs.task.manga.MangaTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务清单。
 * <p>
 * 老实现靠反射扫类、再按类名排序决定执行顺序，注释里还写着"在 Linux 中并不是字典排序我就很迷茫"。
 * 顺序本来就是业务需求（比如要先领到 B 币券才谈得上花掉它），按需要的顺序直接列出来更清楚。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public final class TaskRegistry {

    private TaskRegistry() {
    }

    /** 按执行顺序排列 */
    private static final List<Class<? extends Task>> TASKS = List.of(
            // 先领本月的大会员权益，B 币券到账之后下一个任务才有的花
            CollectVipGift.class,
            BiCoinApply.class,
            // 观看、分享，然后投币
            DailyTask.class,
            ThrowCoinTask.class,
            // 直播签到要排在送礼物前面，签到礼物到账才能送出去
            BiLiveTask.class,
            GiveGiftTask.class,
            Silver2CoinTask.class,
            MangaTask.class
    );

    /**
     * 任务类清单。
     *
     * @return 按执行顺序排列的任务类
     */
    public static List<Class<? extends Task>> tasks() {
        return TASKS;
    }

    /**
     * 实例化全部任务。
     *
     * @return 任务实例，构造失败的会被跳过
     */
    public static List<Task> instantiate() {
        List<Task> tasks = new ArrayList<>(TASKS.size());
        for (Class<? extends Task> clazz : TASKS) {
            try {
                tasks.add(clazz.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                log.error("💔任务 [{}] 创建失败: ", clazz.getSimpleName(), e);
            }
        }
        return tasks;
    }
}
