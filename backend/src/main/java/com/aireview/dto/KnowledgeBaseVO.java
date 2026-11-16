package com.aireview.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(
    Long id,
    Long ownerId,
    String name,
    String description,
    /** 当前用户在该知识库中的角色（OWNER/EDITOR/VIEWER）。 */
    String memberRole,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
