package com.aireview.dto;

/**
 * PAT 创建响应：原始令牌仅在此返回一次，客户端必须立即保存。
 */
public record PatCreatedVO(Long id, String uid, String token, String description,
                           Long createdAt, Long expiresTs) {
}
