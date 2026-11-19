package com.aireview.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aireview.service.BackupService;
import com.aireview.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** 保留期清理只删本应用生成、已过期且未标记长期保留的归档。 */
@TestPropertySource(properties = {
    "ai-review.persistence.scheduler-enabled=false",
    "ai-review.persistence.redis-lock-enabled=false"
})
class RetentionCleanerTest extends AbstractIntegrationTest {

    @Autowired private BackupService backupService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir private Path dir;

    @BeforeEach
    void seed() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE backup_record");
        jdbcTemplate.execute("TRUNCATE TABLE instance_setting");
        String json = "{\"enabled\":true,\"storagePath\":\"" + dir.toString().replace("\\", "\\\\")
            + "\",\"autoBackup\":false,\"backupCron\":\"0 0 3 * * *\",\"retentionDays\":1}";
        jdbcTemplate.update("INSERT INTO instance_setting (name, value, description) VALUES ('PERSISTENCE', ?, 'test')", json);

        addRecord("aireview-20260101-000000-manual.zip", "2026-01-01 00:00:00", 0);
        addRecord("aireview-20991231-000000-auto.zip", "2099-12-31 00:00:00", 0);
        addRecord("aireview-20260101-000001-manual.zip", "2026-01-01 00:00:00", 1);
        for (String name : new String[]{
            "aireview-20260101-000000-manual.zip", "aireview-20991231-000000-auto.zip",
            "aireview-20260101-000001-manual.zip", "manual-notes.zip"}) {
            Files.writeString(dir.resolve(name), "zip");
        }
    }

    @Test
    void keepsRecentForeverAndForeignFiles() throws Exception {
        backupService.cleanExpired();

        assertFalse(Files.exists(dir.resolve("aireview-20260101-000000-manual.zip")));
        assertTrue(Files.exists(dir.resolve("aireview-20991231-000000-auto.zip")));
        assertTrue(Files.exists(dir.resolve("aireview-20260101-000001-manual.zip")));
        assertTrue(Files.exists(dir.resolve("manual-notes.zip")));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class));
    }

    private void addRecord(String name, String startedAt, int keepForever) {
        jdbcTemplate.update("INSERT INTO backup_record (file_name, trigger_type, status, size_bytes, "
                + "checksum, table_count, row_count, keep_forever, started_at) "
                + "VALUES (?, 'MANUAL', 'SUCCESS', 4, 'abc', 18, 5, ?, ?)",
            name, keepForever, java.sql.Timestamp.valueOf(startedAt));
    }
}
