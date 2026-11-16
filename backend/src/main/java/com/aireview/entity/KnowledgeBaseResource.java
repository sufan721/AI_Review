package com.aireview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_base_resource")
public class KnowledgeBaseResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    /** 资料类型，对应 {@code com.aireview.index.ResourceType#name()}：NOTE / DOCUMENT。 */
    private String resourceType;

    private Long resourceId;

    private LocalDateTime createdAt;
}
