package com.aireview.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseResourceVO(
    Long resourceId,
    String resourceType,
    /** 资料名称：NOTE 为标题，DOCUMENT 为文件名。 */
    String name,
    /** 资料索引状态（PENDING/INDEXED/FAILED）。 */
    String indexStatus,
    LocalDateTime addedAt
) {
}
