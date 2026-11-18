package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前用户改密码请求：校验旧密码后写入新哈希并自增 token_version。 */
public record ChangePasswordRequest(
    @NotBlank String oldPassword,
    @NotBlank @Size(min = 6, max = 64) String newPassword) {
}
