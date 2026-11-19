package com.aireview.dto;

/** 归档校验结果。reason 为 null 表示通过。 */
public record BackupVerifyVO(boolean valid, String reason) {

    public static BackupVerifyVO ok() {
        return new BackupVerifyVO(true, null);
    }

    public static BackupVerifyVO fail(String reason) {
        return new BackupVerifyVO(false, reason);
    }
}
