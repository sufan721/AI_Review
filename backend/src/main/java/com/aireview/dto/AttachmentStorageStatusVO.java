package com.aireview.dto;

/** 附件存储概览：当前模式、两种模式各自的附件数与字节数、目录是否可写。 */
public record AttachmentStorageStatusVO(
    String mode,
    String localDir,
    boolean directoryWritable,
    long fsCount,
    long fsBytes,
    long dbCount,
    long dbBytes) {
}
