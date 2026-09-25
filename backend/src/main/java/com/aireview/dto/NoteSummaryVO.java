package com.aireview.dto;

import java.time.LocalDateTime;

/** 列表项，不含正文，避免一页拉回大量 Markdown。 */
public record NoteSummaryVO(
    Long id,
    String title,
    Boolean isPinned,
    Boolean isArchived,
    String indexStatus,
    String indexError,
    LocalDateTime updatedAt
) {
}
