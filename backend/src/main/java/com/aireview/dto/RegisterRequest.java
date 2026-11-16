package com.aireview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需为 3-32 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    String username,

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$",
        message = "密码需 8-32 位，含大小写字母、数字和特殊字符")
    String password,

    @NotBlank(message = "确认密码不能为空")
    String confirmPassword,

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    String phone,

    @NotBlank(message = "短信验证码不能为空")
    String smsCode,

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    String nickname,

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不合法")
    String email
) {
}
