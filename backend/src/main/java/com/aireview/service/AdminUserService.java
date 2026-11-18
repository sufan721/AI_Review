package com.aireview.service;

/**
 * 管理员用户管理服务：角色分配、状态变更、删号，均带「最后一个活跃 admin 保护」。
 *
 * <p>对应 memos 的 ErrLastInstanceAdmin：实例必须保留至少一个活跃 ROLE_ADMIN，
 * 否则降级/禁用/删除最后一个 admin 时拒绝。
 */
public interface AdminUserService {

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
}
