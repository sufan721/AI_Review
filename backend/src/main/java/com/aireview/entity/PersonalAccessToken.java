package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 个人访问令牌（PAT），对齐 memos 的 PersonalAccessToken。
 * 仅存令牌摘要（SHA-256），原始值只在创建时返回一次。
 */
@Data
@TableName("personal_access_token")
public class PersonalAccessToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户可见短标识。 */
    private String uid;

    private Long userId;

    /** 令牌摘要。 */
    private String tokenHash;

    /** 描述。 */
    private String description;

    /** epoch 毫秒。 */
    private Long createdAt;

    /** 过期时间 epoch 毫秒，null 表示不过期。 */
    private Long expiresTs;
}
