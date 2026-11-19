package com.aireview.dto;

import java.time.LocalDateTime;

/** 附件视图，不返回二进制内容本身。 */
public record AttachmentVO(
    Long id,
    String originalName,
    String contentType,
    long sizeBytes,
    String sha256,
    String storageMode,
    LocalDateTime createdAt) {
}
