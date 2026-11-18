package com.aireview.dto;

import java.util.List;

/**
 * 用户资料视图（memos 语义扩展），含 displayName/description/avatarUrl。
 */
public record UserProfileVO(
    Long id,
    String username,
    String displayName,
    String description,
    String avatarUrl,
    List<String> roles,
    Long createdAt
) {
}
