package com.aireview.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新当前用户资料的请求（memos 语义：display_name / description / avatar_url）。
 */
public record UpdateProfileRequest(
    @Size(max = 64, message = "昵称长度不能超过 64") String displayName,
    @Size(max = 512, message = "个人描述长度不能超过 512") String description,
    @Size(max = 512, message = "头像 URL 长度不能超过 512") String avatarUrl
) {
}
