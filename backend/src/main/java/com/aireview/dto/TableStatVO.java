package com.aireview.dto;

import lombok.Data;

/** 单张表的行数估算与占用空间（来自 information_schema）。 */
@Data
public class TableStatVO {
    private String tableName;
    private Long rowEstimate;
    private Long sizeBytes;
}
