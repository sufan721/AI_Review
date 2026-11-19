package com.aireview.mapper;

import com.aireview.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    @Select("""
        SELECT r.role_code
        FROM sys_role r
        JOIN sys_user_role ur ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
        SELECT p.perm_code
        FROM sys_permission p
        JOIN sys_role_permission rp ON p.id = rp.perm_id
        JOIN sys_user_role ur ON rp.role_id = ur.role_id
        WHERE ur.user_id = #{userId}
        """)
    List<String> selectPermCodes(@Param("userId") Long userId);

    @Insert("""
        INSERT IGNORE INTO sys_user_role (user_id, role_id)
        SELECT #{userId}, id FROM sys_role WHERE role_code = #{roleCode}
        """)
    int bindRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    /** 原子更新失败计数与锁定时间，避免读改写丢失。 */
    @Update("""
        UPDATE `user` SET fail_count = #{failCount}, lock_until = #{lockUntil} WHERE id = #{userId}
        """)
    int updateFailState(@Param("userId") Long userId, @Param("failCount") int failCount,
        @Param("lockUntil") LocalDateTime lockUntil);

    @Update("UPDATE `user` SET fail_count = 0, lock_until = NULL WHERE id = #{userId}")
    int resetFailState(@Param("userId") Long userId);

    @Update("UPDATE `user` SET password_hash = #{hash}, fail_count = 0, lock_until = NULL WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("hash") String hash);

    /** 改密码/重置密码时会话版本号 +1，使旧 JWT 失效。 */
    @Update("UPDATE `user` SET password_hash = #{hash}, token_version = token_version + 1, "
        + "fail_count = 0, lock_until = NULL WHERE id = #{userId}")
    int updatePasswordAndBumpVersion(@Param("userId") Long userId, @Param("hash") String hash);

    /** 仅自增会话版本号（管理员改角色/状态/重置密码等场景踢掉旧会话）。 */
    @Update("UPDATE `user` SET token_version = token_version + 1 WHERE id = #{userId}")
    int bumpTokenVersion(@Param("userId") Long userId);

    /**
     * 初始管理员（首位注册并持有 ROLE_ADMIN 的账号）的 id：取持有 ROLE_ADMIN 的最小 user.id。
     *
     * <p>首位注册用户在建库后必然是最小 id，且注册时即被授予 ADMIN，因此该值稳定指向
     * 「初始管理员」。若历史上初始管理员已被降权/删除，则取当前最小 id 的 admin 兜底；
     * 无任何 admin 时返回 null。
     */
    @Select("""
        SELECT MIN(u.id)
        FROM `user` u
        JOIN sys_user_role ur ON u.id = ur.user_id
        JOIN sys_role r ON ur.role_id = r.id
        WHERE r.role_code = 'ROLE_ADMIN'
        """)
    Long selectInitialAdminId();

    /** 当前活跃（status=1）且持有 ROLE_ADMIN 的用户数，用于「最后一个 admin」保护。 */
    @Select("""
        SELECT COUNT(*)
        FROM `user` u
        JOIN sys_user_role ur ON u.id = ur.user_id
        JOIN sys_role r ON ur.role_id = r.id
        WHERE u.status = 1 AND r.role_code = 'ROLE_ADMIN'
        """)
    long countActiveAdmins();

    /** 移除某用户的 ROLE_ADMIN（不删除用户）。 */
    @Update("""
        DELETE ur FROM sys_user_role ur
        JOIN sys_role r ON ur.role_id = r.id
        WHERE ur.user_id = #{userId} AND r.role_code = 'ROLE_ADMIN'
        """)
    int revokeAdminRole(@Param("userId") Long userId);

    /** 给某用户授予 ROLE_ADMIN（INSERT IGNORE，幂等）。 */
    @Insert("""
        INSERT IGNORE INTO sys_user_role (user_id, role_id)
        SELECT #{userId}, id FROM sys_role WHERE role_code = 'ROLE_ADMIN'
        """)
    int grantAdminRole(@Param("userId") Long userId);

    /** 设置用户状态（1 正常 / 0 禁用）。 */
    @Update("UPDATE `user` SET status = #{status} WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") int status);
}
