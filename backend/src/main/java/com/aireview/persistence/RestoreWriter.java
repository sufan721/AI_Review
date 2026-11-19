package com.aireview.persistence;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.RestoreResultVO;
import com.aireview.mapper.BackupDumpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 真正的覆盖写入：整个恢复在一个事务里完成，任一步失败都会回滚到恢复前状态。
 * 独立成 bean 是为了让 {@code @Transactional} 通过代理生效（同类内部调用不会走代理）。
 */
@Service
public class RestoreWriter {

    private static final int BATCH_SIZE = 500;

    private final BackupDumpMapper dumpMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public RestoreWriter(BackupDumpMapper dumpMapper) {
        this.dumpMapper = dumpMapper;
    }

    @Transactional
    public RestoreResultVO write(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            JsonNode manifest = mapper.readTree(readEntry(zip, "manifest.json"));
            if (!manifest.path("formatVersion").isNumber() || manifest.get("formatVersion").asInt() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的归档版本");
            }
            JsonNode hint = mapper.readTree(readEntry(zip, "index-hint.json"));

            // 先逆序清空，再按父表在前的顺序写回；库中无外键约束，顺序仅为可读性与排查友好
            List<String> tables = new ArrayList<>(BackupTables.ALL);
            for (int i = tables.size() - 1; i >= 0; i--) {
                dumpMapper.deleteRows(tables.get(i));
            }
            long restored = 0;
            for (String table : tables) {
                ZipEntry entry = zip.getEntry("data/" + table + ".jsonl");
                if (entry == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "归档缺少表数据：" + table);
                }
                List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        batch.add(mapper.readValue(line,
                            mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)));
                        if (batch.size() >= BATCH_SIZE) {
                            dumpMapper.insertRows(table, batch);
                            restored += batch.size();
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    dumpMapper.insertRows(table, batch);
                    restored += batch.size();
                }
            }
            int reindex = 0;
            JsonNode notes = hint.path("notes");
            if (notes.isArray()) {
                reindex += ((ArrayNode) notes).size();
            }
            JsonNode documents = hint.path("documents");
            if (documents.isArray()) {
                reindex += ((ArrayNode) documents).size();
            }
            return new RestoreResultVO(restored, reindex);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "归档读取失败：" + exception.getMessage());
        }
    }

    private byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("归档缺少 " + name);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }
}
