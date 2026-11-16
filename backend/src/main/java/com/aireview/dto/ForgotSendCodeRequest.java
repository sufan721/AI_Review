package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotSendCodeRequest(
    @NotBlank(message = "账号不能为空")
    String account
) {
}
