package com.aireview.support;

import com.aireview.index.IndexTaskSink;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 可控的索引任务投递目标：{@code submit} 仅入队不执行，保证 create/update 后状态仍为
 * {@code PENDING}（与既有集成测试断言一致）；需要验证索引结果时显式 {@link #drain()} 同步执行。
 */
public class ControlledTaskExecutor implements IndexTaskSink {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void submit(Runnable task) {
        tasks.add(task);
    }

    /** 当前待执行任务数。 */
    public int pendingCount() {
        return tasks.size();
    }

    /** 同步执行队列中全部任务并清空队列。 */
    public void drain() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    /** 丢弃队列中未执行的任务。 */
    public void clear() {
        tasks.clear();
    }
}
