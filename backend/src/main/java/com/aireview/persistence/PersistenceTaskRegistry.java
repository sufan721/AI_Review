package com.aireview.persistence;

import com.aireview.dto.PersistenceTaskVO;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 后台任务进度（备份与恢复）。只存在进程内存：任务完成后前端轮询到终态即可，
 * 数据库侧的一致性由 {@code backup_record} 与恢复事务保证。
 */
@Component
public class PersistenceTaskRegistry {

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, PersistenceTaskVO> tasks = new ConcurrentHashMap<>();

    public long create(String kind, int totalTables) {
        long id = sequence.getAndIncrement();
        tasks.put(id, new PersistenceTaskVO(id, kind, "RUNNING", 0, totalTables, "已排队"));
        return id;
    }

    public void update(long id, String status, int completedTables, String message) {
        PersistenceTaskVO current = tasks.get(id);
        if (current == null) {
            return;
        }
        tasks.put(id, new PersistenceTaskVO(id, current.kind(), status, completedTables,
            current.totalTables(), message));
    }

    /** 是否还有任务在跑；用于阻止备份与恢复互相踩踏。 */
    public boolean hasRunning() {
        return tasks.values().stream().anyMatch(task -> "RUNNING".equals(task.status()));
    }

    public PersistenceTaskVO get(long id) {
        PersistenceTaskVO task = tasks.get(id);
        return task == null ? new PersistenceTaskVO(id, "UNKNOWN", "NOT_FOUND", 0, 0, "任务不存在或已过期") : task;
    }

    /** 供前端在页面卸载/切换分区时主动清理，避免内存无限增长。 */
    public void remove(long id) {
        tasks.remove(id);
    }
}
