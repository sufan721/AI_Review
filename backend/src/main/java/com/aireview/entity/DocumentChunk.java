package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 资料切片。笔记与上传的 Markdown 文件共用一张表，通过 {@code resourceType} 区分。
 * 每份资料的当前版本对应一组切片，正文变更后由索引任务整体替换。
 */
@Data
@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** {@code note} 或 {@code document}。 */
    private String resourceType;

    private Long resourceId;

    private Integer contentVersion;

    /** 切片序号，从 0 开始。 */
    private Integer chunkIndex;

    private String content;

    /** 切片在清洗后正文中的起始字符位置。 */
    private Integer positionStart;

    /** 切片在清洗后正文中的结束字符位置（开区间）。 */
    private Integer positionEnd;

    /** 切片内容 SHA-256，用于幂等比对。 */
    private String contentHash;

    private LocalDateTime createdAt;
}
