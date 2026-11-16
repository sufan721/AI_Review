package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;

/** 模拟 IdP 回调：真实 OIDC 授权码流程中，由 IdP 认证后把 subject 回传，此处直接提交。 */
public record SsoLoginRequest(
    @NotBlank(message = "身份提供方不能为空")
    String provider,

    @NotBlank(message = "subject 不能为空")
    String subject,

    String username,
    String email
) {
}
