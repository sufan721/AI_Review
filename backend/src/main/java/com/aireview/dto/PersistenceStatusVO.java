package com.aireview.dto;

import java.util.List;

/** 管理页顶部概览：真实库大小、表行数、索引切片数、目录状态与备份计划。 */
public record PersistenceStatusVO(
    boolean enabled,
    String storagePath,
    boolean directoryWritable,
    long databaseSizeBytes,
    long totalRows,
    long indexChunks,
    List<TableStatVO> tables,
    BackupRecordVO lastBackup,
    String nextRunAt,
    boolean autoBackupEnabled,
    Long runningBackupId) {

    /** 未配置自动备份或周期无法计算时，nextRunAt 传 null，前端显示「未安排」。 */
    public static PersistenceStatusVO of(boolean enabled, String storagePath, boolean directoryWritable,
        long databaseSizeBytes, long totalRows, long indexChunks, List<TableStatVO> tables,
        BackupRecordVO lastBackup, String nextRunAt, boolean autoBackupEnabled, Long runningBackupId) {
        return new PersistenceStatusVO(enabled, storagePath, directoryWritable, databaseSizeBytes,
            totalRows, indexChunks, tables, lastBackup, nextRunAt, autoBackupEnabled, runningBackupId);
    }
}
