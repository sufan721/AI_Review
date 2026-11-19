package com.aireview.dto;

import java.time.LocalDateTime;

/** 备份历史视图：在实体基础上补一个只读进度，供前端轮询显示。 */
public record BackupRecordVO(
    Long id,
    String fileName,
    String triggerType,
    String status,
    Long sizeBytes,
    String checksum,
    Integer tableCount,
    Long rowCount,
    boolean keepForever,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Integer completedTables,
    Integer totalTables) {

    public static BackupRecordVO of(com.aireview.entity.BackupRecord record,
        Integer completedTables, Integer totalTables) {
        return new BackupRecordVO(record.getId(), record.getFileName(), record.getTriggerType(),
            record.getStatus(), record.getSizeBytes(), record.getChecksum(), record.getTableCount(),
            record.getRowCount(), Integer.valueOf(1).equals(record.getKeepForever()),
            record.getErrorMessage(), record.getStartedAt(), record.getFinishedAt(),
            completedTables, totalTables);
    }
}
