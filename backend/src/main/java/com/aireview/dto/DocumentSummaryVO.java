package com.aireview.dto;

import java.time.LocalDateTime;

public record DocumentSummaryVO(
    Long id,
    String fileName,
    Long fileSize,
    Integer contentVersion,
    String indexStatus,
    String indexError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
