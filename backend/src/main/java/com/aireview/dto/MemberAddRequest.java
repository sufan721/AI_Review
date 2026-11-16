package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberAddRequest(
    @NotNull(message = "用户 ID 不能为空")
    Long userId,

    @NotBlank(message = "成员角色不能为空")
    String memberRole
) {
}
