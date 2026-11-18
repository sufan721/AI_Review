package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.mapper.UserMapper;
import com.aireview.security.PermissionService;
import com.aireview.service.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员用户管理实现：所有写操作带「最后一个活跃 admin 保护」。
 *
 * <p>判定口径：先查目标用户当前是否活跃 admin，若是，再查全库活跃 admin 数量；
 * 当数量 <= 1 且本次操作会移除该 admin 身份/状态/记录时，拒绝。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserMapper userMapper;
    private final PermissionService permissionService;

    public AdminUserServiceImpl(UserMapper userMapper, PermissionService permissionService) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public void grantAdmin(Long userId) {
        ensureUserExists(userId);
        userMapper.grantAdminRole(userId);
        // 授权不需要踢旧会话：升级权限应即时生效，旧 JWT 在下次请求时 roles() 已包含 ADMIN
    }

    @Override
    @Transactional
    public void revokeAdmin(Long userId) {
        ensureUserExists(userId);
        if (isActiveAdmin(userId)) {
            guardLastAdmin();
        }
        userMapper.revokeAdminRole(userId);
        // 撤销权限后踢掉旧会话，避免旧 JWT 在权限缓存窗口内仍带 ADMIN
        userMapper.bumpTokenVersion(userId);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, int status) {
        ensureUserExists(userId);
        // 禁用一个活跃 admin 时检查最后一个 admin 保护
        if (status == 0 && isActiveAdmin(userId)) {
            guardLastAdmin();
        }
        userMapper.updateStatus(userId, status);
        // 状态变更后踢旧会话：禁用用户的旧 JWT 不应再可用
        userMapper.bumpTokenVersion(userId);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        ensureUserExists(userId);
        if (isActiveAdmin(userId)) {
            guardLastAdmin();
        }
        // 先 bump token_version，再删用户；即便外键级联残留会话，tv 也不匹配
        userMapper.bumpTokenVersion(userId);
        userMapper.deleteById(userId);
    }

    private void ensureUserExists(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    /** 目标用户当前是否活跃（status=1）且持有 ROLE_ADMIN。 */
    private boolean isActiveAdmin(Long userId) {
        return Integer.valueOf(1).equals(userMapper.selectById(userId).getStatus())
            && permissionService.hasRole(userId, ADMIN_ROLE);
    }

    /** 全库活跃 admin <= 1 时拒绝，对应 memos ErrLastInstanceAdmin。 */
    private void guardLastAdmin() {
        if (userMapper.countActiveAdmins() <= 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "实例必须保留至少一个管理员");
        }
    }
}
