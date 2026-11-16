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
}
