package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForgotResetRequest(
    @NotBlank(message = "账号不能为空")
    String account,

    @NotBlank(message = "重置码不能为空")
    String resetCode,

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$",
        message = "密码需 8-32 位，含大小写字母、数字和特殊字符")
    String newPassword,

    @NotBlank(message = "确认密码不能为空")
    String confirmPassword
) {
}
