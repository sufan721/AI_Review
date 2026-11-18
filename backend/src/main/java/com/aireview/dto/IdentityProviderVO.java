package com.aireview.dto;

/**
 * 身份源视图。
 */
public record IdentityProviderVO(
    String uid,
    String name,
    String type,
    String identifierFilter,
    String config
) {
}
