package com.aireview.index;

/**
 * 索引任务的投递目标。生产实现为线程池（异步）；测试用可控实现捕获任务以保持确定性。
 */
@FunctionalInterface
public interface IndexTaskSink {
    void submit(Runnable task);
}
