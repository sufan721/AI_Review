package com.aireview.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 快照设置的默认值、损坏 JSON 回退与规范化校验。 */
class PersistenceSettingTest {

    @Test
    void defaultsAreSafe() {
        PersistenceSetting setting = PersistenceSetting.defaults();
        assertTrue(setting.enabled());
        assertFalse(setting.autoBackup());
        assertEquals(30, setting.retentionDays());
        assertEquals("0 0 3 * * *", setting.backupCron());
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        assertEquals(PersistenceSetting.defaults(), PersistenceSetting.fromJson("{not json"));
        assertFalse(PersistenceSetting.isValidJson("{not json"));
        assertTrue(PersistenceSetting.isValidJson("{\"enabled\":false}"));
    }

    @Test
    void normalizeRejectsBadCronAndRetention() {
        assertThrows(IllegalArgumentException.class, () ->
            new PersistenceSetting(true, "./data", false, "not a cron", 30).normalize());
        assertThrows(IllegalArgumentException.class, () ->
            new PersistenceSetting(true, "./data", false, "0 0 3 * * *", 0).normalize());
        assertThrows(IllegalArgumentException.class, () ->
            new PersistenceSetting(true, "./data", false, "0 0 3 * * *", 4000).normalize());
    }

    @Test
    void normalizeResolvesRelativePath() {
        PersistenceSetting normalized = new PersistenceSetting(true, "./data/backups", false,
            "0 0 3 * * *", 7).normalize();
        assertTrue(normalized.storagePath().endsWith("data/backups")
            || normalized.storagePath().endsWith("data\\backups"));
        assertTrue(new java.io.File(normalized.storagePath()).isAbsolute());
        assertEquals(7, normalized.retentionDays());
    }
}
