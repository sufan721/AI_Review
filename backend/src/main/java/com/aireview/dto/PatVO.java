package com.aireview.dto;

/**
 * 个人访问令牌视图（不含 token_hash，安全）。
 *
 * <p>原始令牌仅在创建时通过 {@link PatCreatedVO} 返回一次，后续只能看到元信息。
 */
public record PatVO(Long id, String uid, String description, Long createdAt, Long expiresTs) {
}
