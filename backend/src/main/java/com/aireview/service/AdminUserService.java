package com.aireview.service;

/**
 * 管理员用户管理服务：角色分配、状态变更、删号，均带「初始管理员保护」与「最后一个活跃 admin 保护」。
 *
 * <p>初始管理员（首位注册并持有 ROLE_ADMIN 的账号）不可被任何人通过本服务删除 / 降权 /
 * 禁用 / 重置密码 / 改用户名；他自己维护资料与密码走自助接口。
 *
 * <p>对应 memos 的 ErrLastInstanceAdmin：实例必须保留至少一个活跃 ROLE_ADMIN，
 * 否则降级/禁用/删除最后一个 admin 时拒绝。
 */
public interface AdminUserService {

    /**
     * 管理员新建成员：用户名查重后创建账号并绑定 ROLE_USER，初始状态启用。
     * 不受「禁用注册」实例开关限制（管理员主动创建），用户名冲突时拒绝。
     *
     * @return 新建用户的 ID
     */
    Long createUser(String username, String password);

    /** 授予 ROLE_ADMIN；幂等。 */
    void grantAdmin(Long userId);

    /**
     * 撤销 ROLE_ADMIN；若该用户是最后一个活跃 admin 则拒绝。
     * 撤销后自增 token_version，踢掉该用户旧会话。
     */
    void revokeAdmin(Long userId);

    /**
     * 设置用户状态（1 正常 / 0 禁用）；禁用一个活跃 admin 时若他是最后一个则拒绝。
     * 状态变更后自增 token_version。
     */
    void updateStatus(Long userId, int status);

    /**
     * 删除用户；若该用户是最后一个活跃 admin 则拒绝。
     * 删除前自增 token_version 以防外键残留会话误用。
     */
    void deleteUser(Long userId);

    /**
     * 管理员重置用户密码（不校验旧密码）；写入新哈希并自增 token_version，
     * 该用户旧 JWT 立即失效，需用新密码重新登录。
     */
    void changePassword(Long userId, String newPassword);

    /**
     * 管理员修改用户登录用户名；查重后更新并自增 token_version 踢旧会话。
     */
    void changeUsername(Long userId, String newUsername);
}
