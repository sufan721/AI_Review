package com.aireview.security;

import com.aireview.mapper.UserMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 角色/权限查询。不缓存：权限变更即时生效，避免令牌内嵌权限导致的滞后。 */
@Service
public class PermissionService {
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserMapper userMapper;

    public PermissionService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Set<String> roles(Long userId) {
        return new HashSet<>(userMapper.selectRoleCodes(userId));
    }

    public Set<String> permissions(Long userId) {
        return new HashSet<>(userMapper.selectPermCodes(userId));
    }

    public boolean hasRole(Long userId, String role) {
        return roles(userId).contains(role);
    }

    /** 管理员默认放行全部权限，避免为每个新权限都维护一次管理员授权。 */
    public boolean hasPermission(Long userId, String permission) {
        Set<String> roles = roles(userId);
        return roles.contains(ADMIN_ROLE) || permissions(userId).contains(permission);
    }
}
