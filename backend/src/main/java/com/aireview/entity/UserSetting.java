package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户设置，对齐 memos 的 store.UserSetting（key-value，value 为 JSON）。
 */
@Data
@TableName("user_setting")
public class UserSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** GENERAL / TAGS / EDITOR / WEBHOOKS。 */
    private String key;

    /** JSON 字符串。 */
    private String value;
}
