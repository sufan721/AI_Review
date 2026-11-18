package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户 SSO 身份关联，对齐 memos 的 store.UserIdentity。
 */
@Data
@TableName("user_identity")
public class UserIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 身份源标识（identity provider 的 uid）。 */
    private String provider;

    /** 外部用户唯一标识。 */
    private String externUid;

    /** epoch 毫秒。 */
    private Long createdAt;

    /** epoch 毫秒。 */
    private Long updatedAt;
}
