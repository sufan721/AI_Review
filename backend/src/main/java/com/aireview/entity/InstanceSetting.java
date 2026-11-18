package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 实例级系统设置，对齐 memos 的 store.InstanceSetting（key-value，value 为 JSON）。
 */
@Data
@TableName("instance_setting")
public class InstanceSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** BASIC / GENERAL / STORAGE / TAGS / NOTIFICATION / ACCESS / USAGE_LIMITS。 */
    private String name;

    /** JSON 字符串。 */
    private String value;

    private String description;
}
