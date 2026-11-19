package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 一次快照尝试的元数据（含失败记录）。归档字节放在配置的 storagePath 下，
 * 这里只记文件名与校验和，避免「昨天为什么没备份」无从查起。
 */
@Data
@TableName("backup_record")
public class BackupRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归档文件名，不含目录。 */
    private String fileName;

    /** MANUAL / AUTO / PRE_RESTORE。 */
    private String triggerType;

    /** RUNNING / SUCCESS / FAILED。 */
    private String status;

    private Long sizeBytes;

    /** zip 文件字节的 SHA-256，校验完整性用。 */
    private String checksum;

    private Integer tableCount;

    private Long rowCount;

    /** 1=不参与保留期清理。PRE_RESTORE 快照恒为 1。 */
    private Integer keepForever;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
