package com.aireview.dto;

import java.util.List;

/**
 * 管理后台用户视图。roles 为该用户当前持有的角色码集合（如 ROLE_ADMIN）。
 */
public record AdminUserVO(Long id, String username, String nickname, Integer status,
    List<String> roles) {
}
