package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户设置的请求（memos 语义）。key 限定为 GENERAL/TAGS/EDITOR。
 */
public record UpdateUserSettingRequest(
    @NotBlank(message = "设置 key 不能为空") String key,
    @Size(max = 8192, message = "设置值过长") String value
) {
}
