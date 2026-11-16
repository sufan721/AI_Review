package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "用户名不能为空")
    String username,

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不合法")
    String password,

    // 验证码在开启校验时必填，由 Service 判定；这里不强制，便于关闭校验的场景
    String captchaKey,
    String captchaCode
) {
}
