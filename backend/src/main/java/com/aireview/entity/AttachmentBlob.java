package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 数据库存储模式下的附件二进制，主键即 attachment.id（1:1，无自增、无外键约束）。 */
@Data
@TableName("attachment_blob")
public class AttachmentBlob {

    @TableId
    private Long attachmentId;
    private byte[] content;
}
