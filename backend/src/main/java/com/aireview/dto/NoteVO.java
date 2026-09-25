package com.aireview.dto;

import java.time.LocalDateTime;

public record NoteVO(
    Long id,
    String title,
    String content,
    Boolean isPinned,
    Boolean isArchived,
    Integer contentVersion,
    String indexStatus,
    String indexError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
