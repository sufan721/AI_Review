package com.aireview.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 白名单表与 XML 三组语句（select/delete/insert）必须一一对应，新增表时不能漏写。 */
class BackupDumpMapperXmlTest {

    @Test
    void everyWhitelistedTableHasThreeStatements() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/BackupDumpMapper.xml"));
        for (String table : BackupTables.ALL) {
            int occurrences = xml.split("table == '" + table + "'", -1).length - 1;
            assertEquals(3, occurrences, "表 " + table + " 的 XML 语句数不对");
        }
        assertFalse(xml.contains("table == 'backup_record'"), "备份记录表不应进入快照");
    }
}
