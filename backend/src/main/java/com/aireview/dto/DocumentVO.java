package com.aireview.dto;

import java.time.LocalDateTime;

public record DocumentVO(
    Long id,
    String fileName,
    String content,
    Long fileSize,
    Integer contentVersion,
    String indexStatus,
    String indexError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
