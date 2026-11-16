package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 统一身份认证绑定：把 IdP 返回的 subject 映射到本地用户。 */
@Data
@TableName("sys_user_sso_binding")
public class SsoBinding {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String ssoProvider;

    private String ssoSubject;

    private LocalDateTime createdAt;
}
