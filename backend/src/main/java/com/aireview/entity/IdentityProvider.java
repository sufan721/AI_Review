package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 身份源（SSO/OIDC），对齐 memos 的 store.IdentityProvider。
 */
@Data
@TableName("identity_provider")
public class IdentityProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户可见短标识。 */
    private String uid;

    private String name;

    /** OAUTH2 / OIDC。 */
    private String type;

    /** 标识过滤（域名等）。 */
    private String identifierFilter;

    /** 配置 JSON 字符串。 */
    private String config;
}
