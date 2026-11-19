package com.aireview.dto;

/** 恢复结果摘要，供前端在完成提示里显示影响范围。 */
public record RestoreResultVO(long restoredRows, int reindexScheduled) {
}
