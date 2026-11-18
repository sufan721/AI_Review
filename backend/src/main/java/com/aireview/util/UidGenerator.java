package com.aireview.util;

import java.util.UUID;

/**
 * 资源对外短标识（UID）生成器。
 * 对齐 memos 的短标识语义：无连字符 UUID，用于资源名 memos/{uid}、attachments/{uid} 等。
 */
public final class UidGenerator {

    private UidGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
