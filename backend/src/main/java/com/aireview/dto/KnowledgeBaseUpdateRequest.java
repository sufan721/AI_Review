package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "名称不能超过 128 个字符")
    String name,

    @Size(max = 512, message = "描述不能超过 512 个字符")
    String description
) {
}
