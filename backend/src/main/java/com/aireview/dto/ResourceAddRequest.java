package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceAddRequest(
    @NotBlank(message = "资源类型不能为空")
    String resourceType,

    @NotNull(message = "资源 ID 不能为空")
    Long resourceId
) {
}
