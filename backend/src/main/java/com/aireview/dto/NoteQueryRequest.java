package com.aireview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 列表查询条件，keyword 与 tagId 在标签/搜索功能中接入。 */
public record NoteQueryRequest(
    Boolean isPinned,

    Boolean isArchived,

    @Min(value = 1, message = "页码从 1 开始")
    Integer page,

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    Integer size
) {
    public long pageOrDefault() {
        return page == null ? 1L : page;
    }

    public long sizeOrDefault() {
        return size == null ? 20L : size;
    }
}
