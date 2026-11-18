package com.aireview.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新当前用户资料的请求（memos 语义：display_name / description / avatar_url，扩展 username）。
 *
 * <p>username / displayName / description / avatarUrl 均可选，null 表示不修改。
 * username 与 displayName 受实例开关 {@code forbidChangeUsername / forbidChangeNickname} 约束。
 */
public record UpdateProfileRequest(
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间") String username,
    @Size(max = 64, message = "昵称长度不能超过 64") String displayName,
    @Size(max = 512, message = "个人描述长度不能超过 512") String description,
    @Size(max = 512, message = "头像 URL 长度不能超过 512") String avatarUrl
) {
}
