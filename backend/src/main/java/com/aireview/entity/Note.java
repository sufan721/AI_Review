package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("note")
public class Note {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** Markdown 正文。 */
    private String content;

    /** 显式指定列名：MyBatis-Plus 对 isXxx 字段的自动驼峰转换曾出现歧义。 */
    @TableField("is_pinned")
    private Boolean isPinned;

    @TableField("is_archived")
    private Boolean isArchived;

    /** 正文每次有效变更递增，供索引侧判断切片是否过期。 */
    private Integer contentVersion;

    private String indexStatus;

    /** 最近一次索引失败原因，仅在 indexStatus = FAILED 时有值。 */
    private String indexError;

    /** 索引重试次数。 */
    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
