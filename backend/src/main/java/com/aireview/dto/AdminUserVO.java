package com.aireview.dto;

import java.util.List;

/**
 * 管理后台用户视图。roles 为该用户当前持有的角色码集合（如 ROLE_ADMIN）。
 *
 * <p>initialAdmin 标记实例的初始管理员（首位注册账号）：该账号不可被任何人通过管理接口
 * 删除 / 降权 / 禁用 / 重置密码 / 改用户名，前端据此隐藏操作入口。
 */
public record AdminUserVO(Long id, String username, String nickname, Integer status,
    List<String> roles, boolean initialAdmin) {
}
