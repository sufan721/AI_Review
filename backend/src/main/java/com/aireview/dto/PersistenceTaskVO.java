package com.aireview.dto;

/** 异步任务进度（BACKUP / RESTORE），只存在进程内存中。 */
public record PersistenceTaskVO(
    long id,
    String kind,
    String status,
    int completedTables,
    int totalTables,
    String message) {
}
