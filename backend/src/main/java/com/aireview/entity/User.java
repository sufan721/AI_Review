package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，禁止出现在任何对外 DTO 中。 */
    private String passwordHash;

    private String nickname;

    private String phone;

    private String email;

    private String realName;

    /** 个人描述（memos 语义）。 */
    private String description;

    /** 头像 URL（memos 语义）。 */
    private String avatarUrl;

    /** 会话版本号：改密码/重置时 +1，JWT claims 带 tv 比对不一致即 401。 */
    private Integer tokenVersion;

    /** 1 正常，0 禁用。 */
    private Integer status;

    /** 连续登录失败次数，登录成功后清零。 */
    private Integer failCount;

    /** 锁定截止时间，NULL 表示未锁定。 */
    private LocalDateTime lockUntil;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
