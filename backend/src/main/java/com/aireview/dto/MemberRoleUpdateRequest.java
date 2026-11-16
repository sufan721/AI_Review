package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberRoleUpdateRequest(
    @NotBlank(message = "成员角色不能为空")
    String memberRole
) {
}
