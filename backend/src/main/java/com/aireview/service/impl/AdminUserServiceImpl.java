package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.PermissionService;
import com.aireview.service.AdminUserService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员用户管理实现：所有写操作带「初始管理员保护」与「最后一个活跃 admin 保护」。
 *
 * <p>初始管理员 = 首位注册并持有 ROLE_ADMIN 的账号（持有 ADMIN 的最小 user.id）。该账号
 * 是实例的所有者与最终兜底，任何管理员（包括其他被提权的管理员）都不能删除、降权、禁用、
 * 重置密码或改用户名；只能由本人走自助通道（{@code /api/users/me}、{@code /api/auth/change-password}）
 * 维护自己的资料与密码。
 *
 * <p>兜底口径：先查目标用户当前是否活跃 admin，若是，再查全库活跃 admin 数量；
 * 当数量 <= 1 且本次操作会移除该 admin 身份/状态/记录时，拒绝。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE = "ROLE_USER";

    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(UserMapper userMapper, PermissionService permissionService,
        PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Long createUser(String username, String password) {
        String trimmed = username.trim();
        Long dup = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, trimmed));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "用户名已被占用");
        }
        User user = new User();
        user.setUsername(trimmed);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(trimmed);
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 并发下先查后插撞唯一索引，以索引为准
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "用户名已被占用");
        }
        userMapper.bindRole(user.getId(), USER_ROLE);
        return user.getId();
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
        guardInitialAdmin(userId, "撤销管理员权限");
        if (isActiveAdmin(userId)) {
            guardLastAdmin();
        }
        guardSelf(userId, "降权", "请由其他管理员操作");
        userMapper.revokeAdminRole(userId);
        // 撤销权限后踢掉旧会话，避免旧 JWT 在权限缓存窗口内仍带 ADMIN
        userMapper.bumpTokenVersion(userId);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, int status) {
        ensureUserExists(userId);
        if (status == 0) {
            guardInitialAdmin(userId, "禁用");
            // 禁用一个活跃 admin 时检查最后一个 admin 保护
            if (isActiveAdmin(userId)) {
                guardLastAdmin();
            }
            guardSelf(userId, "禁用", "请由其他管理员操作");
        }
        userMapper.updateStatus(userId, status);
        // 状态变更后踢旧会话：禁用用户的旧 JWT 不应再可用
        userMapper.bumpTokenVersion(userId);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        ensureUserExists(userId);
        guardInitialAdmin(userId, "删除");
        if (isActiveAdmin(userId)) {
            guardLastAdmin();
        }
        guardSelf(userId, "删除", "请由其他管理员操作");
        // 先 bump token_version，再删用户；即便外键级联残留会话，tv 也不匹配
        userMapper.bumpTokenVersion(userId);
        userMapper.deleteById(userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newPassword) {
        ensureUserExists(userId);
        guardInitialAdmin(userId, "重置密码");
        guardSelf(userId, "改密码", "请前往个人资料页修改");
        // 管理员重置密码不校验旧密码；写入新哈希并自增 token_version，旧会话立即失效
        userMapper.updatePasswordAndBumpVersion(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    @Transactional
    public void changeUsername(Long userId, String newUsername) {
        ensureUserExists(userId);
        guardInitialAdmin(userId, "修改用户名");
        guardSelf(userId, "改用户名", "请前往个人资料页修改");
        String trimmed = newUsername.trim();
        // 排除当前用户后查重，避免与他人冲突
        Long dup = userMapper.selectCount(Wrappers.<User>lambdaQuery()
            .eq(User::getUsername, trimmed)
            .ne(User::getId, userId));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "用户名已被占用");
        }
        User update = new User();
        update.setId(userId);
        update.setUsername(trimmed);
        userMapper.updateById(update);
        // 用户名变更后踢旧会话：旧 JWT 仍可用但语义已不一致，强制重新登录
        userMapper.bumpTokenVersion(userId);
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

    /**
     * 第二重兜底：全库活跃 admin <= 1 时拒绝，对应 memos ErrLastInstanceAdmin。
     *
     * <p>正常情况下初始管理员保护已经保证至少有 1 个 admin，这里只在初始管理员标识无法
     * 解析（例如历史数据里 admin 角色被手工改动）时兜住最后一道门。
     */
    private void guardLastAdmin() {
        if (userMapper.countActiveAdmins() <= 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "实例必须保留至少一个管理员");
        }
    }

    /**
     * 初始管理员不可被任何人通过管理接口删除 / 降权 / 禁用 / 重置密码 / 改用户名。
     *
     * <p>只有「最后一个 admin」保护不够：一旦把普通成员提权成管理员，新管理员就能把
     * 初始管理员删掉或降权，实例随即失去真正的所有者。这里以库中持有 ROLE_ADMIN 的
     * 最小 user.id 作为初始管理员做硬拦截，不依赖前端是否隐藏按钮。
     *
     * <p>初始管理员本人维护资料与密码请走 {@code /api/users/me} 与
     * {@code /api/auth/change-password}，不受此限制。
     */
    private void guardInitialAdmin(Long userId, String action) {
        Long initialAdminId = userMapper.selectInitialAdminId();
        if (initialAdminId != null && initialAdminId.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "初始管理员不可" + action + "，该账号是实例的原始管理员，仅本人可维护自己的资料与密码");
        }
    }

    /**
     * 禁止管理员在成员管理里操作自己的账号（降权 / 禁用 / 删除 / 改密码 / 改用户名）。
     *
     * <p>「最后一个活跃 admin 保护」只在全库仅剩一个 admin 时生效，多 admin 场景下
     * 自我降权或删号仍会放行，容易导致误操作把自己锁在实例外。这里用登录上下文
     * （由 JwtInterceptor 写入，不信任前端传参）做硬拦截。
     *
     * <p>自己的资料与密码请走 {@code /api/users/me} 与 {@code /api/auth/change-password}，
     * 管理接口只用于管理其他成员。
     */
    private void guardSelf(Long userId, String action, String hint) {
        Long current = UserContext.get();
        if (current != null && current.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "不能对自己" + action + "，" + hint);
        }
    }
}
