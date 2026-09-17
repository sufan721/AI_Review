package com.aireview.dto;

import jakarta.validation.constraints.Size;

public record NoteUpdateRequest(
    @Size(max = 255, message = "标题不能超过 255 个字符")
    String title,

    String content
) {
}
