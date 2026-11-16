package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 登录审计日志，只写不读，用于排查登录问题。 */
@Data
@TableName("sys_login_log")
public class LoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String loginType;

    private Boolean success;

    private String clientIp;

    private String userAgent;

    private String message;

    private LocalDateTime createdAt;
}
