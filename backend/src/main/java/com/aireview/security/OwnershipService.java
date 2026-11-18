package com.aireview.security;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import org.springframework.stereotype.Component;

/**
 * 资源归属统一校验（对齐 memos Authorizer 的资源越权分支）。
 *
 * <p>实例级 admin 可越过归属读取/管理任意单条资源；
 * 知识库内操作不经过本组件，继续走 {@code knowledge_base_member} 成员表语义
 * （admin 不自动成为成员，但可读单条资源）。
 *
 * <p>用法：service 层先按 id 取出资源，再调用
 * {@link #requireOwnerOrAdmin(Long, Long)} 校验 currentUserId 与资源 user_id。
 */
@Component
public class OwnershipService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final PermissionService permissionService;

    public OwnershipService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 本人或实例管理员放行；否则抛 403。 */
    public void requireOwnerOrAdmin(Long currentUserId, Long resourceUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        boolean owner = currentUserId.equals(resourceUserId);
        boolean admin = permissionService.hasRole(currentUserId, ADMIN_ROLE);
        if (!owner && !admin) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 当前用户是否实例管理员。 */
    public boolean isAdmin(Long userId) {
        return userId != null && permissionService.hasRole(userId, ADMIN_ROLE);
    }
}
