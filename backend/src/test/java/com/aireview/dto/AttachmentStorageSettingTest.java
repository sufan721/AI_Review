package com.aireview.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

/** 附件存储设置：默认值（本地文件系统/推荐）、损坏 JSON 回退、枚举解析与规范化校验。 */
class AttachmentStorageSettingTest {

    @Test
    void defaultsAreLocalFs() {
        AttachmentStorageSetting setting = AttachmentStorageSetting.defaults();
        assertEquals(AttachmentStorageSetting.Mode.LOCAL_FS, setting.mode());
        assertEquals(20, setting.maxSizeMb());
        assertEquals("./data/attachments", setting.localDir());
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        assertEquals(AttachmentStorageSetting.defaults(), AttachmentStorageSetting.fromJson("{not json"));
        assertFalse(AttachmentStorageSetting.isValidJson("{not json"));
        assertTrue(AttachmentStorageSetting.isValidJson("{\"mode\":\"DATABASE\"}"));
    }

    @Test
    void parsesModeFromJson() {
        AttachmentStorageSetting database =
            AttachmentStorageSetting.fromJson("{\"mode\":\"database\",\"maxSizeMb\":50}");
        assertEquals(AttachmentStorageSetting.Mode.DATABASE, database.mode());
        assertEquals(50, database.maxSizeMb());
        // 未知枚举回退默认
        assertEquals(AttachmentStorageSetting.Mode.LOCAL_FS,
            AttachmentStorageSetting.fromJson("{\"mode\":\"S3\"}").mode());
    }

    @Test
    void normalizeRejectsBadModeAndSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentStorageSetting(null, "./data", 20).normalize());
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentStorageSetting(AttachmentStorageSetting.Mode.LOCAL_FS, "./data", 0).normalize());
        assertThrows(IllegalArgumentException.class, () ->
            new AttachmentStorageSetting(AttachmentStorageSetting.Mode.LOCAL_FS, "./data", 201).normalize());
    }

    @Test
    void normalizeResolvesRelativePath() {
        AttachmentStorageSetting normalized = new AttachmentStorageSetting(
            AttachmentStorageSetting.Mode.LOCAL_FS, "./data/attachments", 30).normalize();
        assertTrue(normalized.localDir().endsWith("data/attachments")
            || normalized.localDir().endsWith("data\\attachments"));
        assertTrue(new File(normalized.localDir()).isAbsolute());
        assertEquals(30, normalized.maxSizeMb());
    }

    @Test
    void toJsonRoundTrips() {
        AttachmentStorageSetting original =
            new AttachmentStorageSetting(AttachmentStorageSetting.Mode.DATABASE, "./x", 10);
        assertEquals(original, AttachmentStorageSetting.fromJson(original.toJson()));
    }
}
