package com.aireview.dto;

import java.time.LocalDateTime;

public record KnowledgeBaseMemberVO(
    Long userId,
    String username,
    String nickname,
    String memberRole,
    LocalDateTime createdAt
) {
}
