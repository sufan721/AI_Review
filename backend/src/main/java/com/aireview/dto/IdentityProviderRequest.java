package com.aireview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建/更新身份源的请求。
 */
public record IdentityProviderRequest(
    @NotBlank(message = "名称不能为空") @Size(max = 128) String name,
    @NotBlank(message = "类型不能为空") String type,
    String identifierFilter,
    String config
) {
}
