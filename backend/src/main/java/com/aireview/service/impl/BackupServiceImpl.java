package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.PageResult;
import com.aireview.common.ResultCode;
import com.aireview.dto.BackupRecordVO;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.PersistenceSetting;
import com.aireview.dto.PersistenceStatusVO;
import com.aireview.dto.TableStatVO;
import com.aireview.entity.BackupRecord;
import com.aireview.mapper.BackupDumpMapper;
import com.aireview.mapper.BackupRecordMapper;
import com.aireview.mapper.DocumentChunkMapper;
import com.aireview.mapper.PersistenceStatMapper;
import com.aireview.persistence.BackupLock;
import com.aireview.persistence.BackupTables;
import com.aireview.persistence.PersistenceTaskRegistry;
import com.aireview.service.BackupService;
import com.aireview.service.InstanceSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 快照实现：流式 MyBatis 导出 → zip 归档 → 校验和落库 → 保留期清理。 */
@Service
public class BackupServiceImpl implements BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupServiceImpl.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ARCHIVE_NAME = Pattern.compile(
        "^aireview-\\d{8}-\\d{6}-(manual|auto|pre-restore)(-\\d+)?\\.zip$");
    private static final int BATCH_SIZE = 500;

    private final BackupRecordMapper recordMapper;
    private final BackupDumpMapper dumpMapper;
    private final PersistenceStatMapper statMapper;
    private final DocumentChunkMapper chunkMapper;
    private final InstanceSettingService settingService;
    private final PersistenceTaskRegistry tasks;
    private final BackupLock lock;
    private final TaskExecutor backupExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai-review.persistence.auto-backup-enabled:true}")
    private boolean autoBackupEnabled;

    public BackupServiceImpl(BackupRecordMapper recordMapper, BackupDumpMapper dumpMapper,
        PersistenceStatMapper statMapper, DocumentChunkMapper chunkMapper,
        InstanceSettingService settingService, PersistenceTaskRegistry tasks, BackupLock lock,
        @Qualifier("backupTaskExecutor") TaskExecutor backupExecutor) {
        this.recordMapper = recordMapper;
        this.dumpMapper = dumpMapper;
        this.statMapper = statMapper;
        this.chunkMapper = chunkMapper;
        this.settingService = settingService;
        this.tasks = tasks;
        this.lock = lock;
        this.backupExecutor = backupExecutor;
    }

    @Override
    public synchronized Long start(String trigger) {
        if (recordMapper.selectRunning() != null) {
            throw new BusinessException(ResultCode.CONFLICT, "已有备份任务在执行，请稍后再试");
        }
        String lockToken = lock.acquire();
        if (lockToken == null) {
            throw new BusinessException(ResultCode.CONFLICT, "其他实例正在备份，请稍后再试");
        }
        PersistenceSetting setting = settingService.getPersistence();
        BackupRecord record = newRecord(trigger, setting, false);
        recordMapper.insert(record);
        long taskId = tasks.create("BACKUP", BackupTables.ALL.size());
        try {
            backupExecutor.execute(() -> run(record.getId(), taskId, lockToken));
        } catch (RuntimeException exception) {
            lock.release(lockToken);
            record.setStatus("FAILED");
            record.setErrorMessage("备份队列已满");
            record.setFinishedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            tasks.update(taskId, "FAILED", 0, "备份队列已满");
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "备份队列已满，请稍后重试");
        }
        return record.getId();
    }

    @Override
    public synchronized BackupRecord snapshotForRestore() {
        if (recordMapper.selectRunning() != null) {
            throw new BusinessException(ResultCode.CONFLICT, "已有备份任务在执行，无法创建恢复前快照");
        }
        String lockToken = lock.acquire();
        if (lockToken == null) {
            throw new BusinessException(ResultCode.CONFLICT, "其他实例正在备份，无法创建恢复前快照");
        }
        BackupRecord record = newRecord("PRE_RESTORE", settingService.getPersistence(), true);
        recordMapper.insert(record);
        // 安全快照必须同步完成：拿不到它就不允许继续恢复
        run(record.getId(), -1, lockToken);
        if (!"SUCCESS".equals(recordMapper.selectById(record.getId()).getStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "恢复前快照创建失败，已中止恢复");
        }
        return record;
    }

    @Override
    public void run(long recordId, long taskId, String lockToken) {
        BackupRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            lock.release(lockToken);
            return;
        }
        PersistenceSetting setting = settingService.getPersistence();
        Path root = Path.of(setting.storagePath()).toAbsolutePath().normalize();
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".aireview-", ".zip.tmp");
            BackupSummary summary = writeArchive(temporary, record, taskId);
            Path target = root.resolve(record.getFileName());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            record.setStatus("SUCCESS");
            record.setSizeBytes(Files.size(target));
            record.setChecksum(sha256(target));
            record.setTableCount(summary.tables());
            record.setRowCount(summary.rows());
            record.setFinishedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            if (taskId > 0) {
                tasks.update(taskId, "SUCCESS", BackupTables.ALL.size(), "备份完成");
            }
            log.info("快照完成：{} ({} 行, {} 张表)", record.getFileName(), summary.rows(), summary.tables());
        } catch (Exception exception) {
            log.error("快照失败：{}", record.getFileName(), exception);
            record.setStatus("FAILED");
            record.setErrorMessage(trimMessage(exception.getMessage()));
            record.setFinishedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            if (taskId > 0) {
                tasks.update(taskId, "FAILED", 0, "备份失败：" + record.getErrorMessage());
            }
            deleteQuietly(temporary);
            deleteQuietly(root.resolve(record.getFileName()));
        } finally {
            lock.release(lockToken);
        }
    }

    /** 逐表流式写 JSONL，最后写 manifest 与索引提示；不在内存里攒全库数据。 */
    private BackupSummary writeArchive(Path target, BackupRecord record, long taskId)
        throws IOException, NoSuchAlgorithmException {
        List<TableInfo> tableInfos = new ArrayList<>();
        List<Long> noteIds = new ArrayList<>();
        List<Long> documentIds = new ArrayList<>();
        long totalRows = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            int completed = 0;
            for (String table : BackupTables.ALL) {
                List<Map<String, Object>> rows = dumpMapper.selectRows(table);
                MessageDigest digest = digest();
                zip.putNextEntry(new ZipEntry("data/" + table + ".jsonl"));
                // 不关闭 writer：关闭会连带关闭 ZipOutputStream，导致后续条目写不进去
                BufferedWriter writer = new BufferedWriter(
                    new java.io.OutputStreamWriter(zip, StandardCharsets.UTF_8));
                for (Map<String, Object> row : rows) {
                    byte[] line = (mapper.writeValueAsString(normalize(row)) + "\n")
                        .getBytes(StandardCharsets.UTF_8);
                    writer.write(new String(line, StandardCharsets.UTF_8));
                    digest.update(line);
                    if ("note".equals(table)) {
                        noteIds.add(((Number) row.get("id")).longValue());
                    } else if ("document".equals(table)) {
                        documentIds.add(((Number) row.get("id")).longValue());
                    }
                }
                writer.flush();
                zip.closeEntry();
                tableInfos.add(new TableInfo(table, rows.size(), HexFormat.of().formatHex(digest.digest())));
                totalRows += rows.size();
                completed++;
                if (taskId > 0) {
                    tasks.update(taskId, "RUNNING", completed, "正在备份 " + table);
                }
            }
            ObjectNode manifest = mapper.createObjectNode();
            manifest.put("formatVersion", 1);
            manifest.put("appVersion", "0.0.1");
            manifest.put("createdAt", LocalDateTime.now().format(SQL_TIME));
            manifest.put("totalRows", totalRows);
            ArrayNode tablesNode = manifest.putArray("tables");
            for (TableInfo info : tableInfos) {
                ObjectNode node = tablesNode.addObject();
                node.put("name", info.name());
                node.put("rows", info.rows());
                node.put("sha256", info.sha256());
            }
            writeJsonEntry(zip, "manifest.json", manifest);

            ObjectNode hint = mapper.createObjectNode();
            ArrayNode notes = hint.putArray("notes");
            noteIds.forEach(notes::add);
            ArrayNode documents = hint.putArray("documents");
            documentIds.forEach(documents::add);
            writeJsonEntry(zip, "index-hint.json", hint);
        }
        return new BackupSummary(tableInfos.size(), totalRows);
    }

    private void writeJsonEntry(ZipOutputStream zip, String name, JsonNode node) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(mapper.writeValueAsBytes(node));
        zip.closeEntry();
    }

    /** JDBC 返回的 LocalDateTime / JSON / byte[] 统一成可安全 round-trip 的类型。 */
    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (value instanceof LocalDateTime time) {
                result.put(key, time.format(SQL_TIME));
            } else if (value instanceof byte[] bytes) {
                result.put(key, Base64.getEncoder().encodeToString(bytes));
            } else if (value instanceof Map || value instanceof List) {
                result.put(key, value.toString());
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    @Override
    public BackupRecordVO get(long id) {
        BackupRecord record = require(id);
        return toVO(record);
    }

    @Override
    public PageResult<BackupRecordVO> list(long page, long size) {
        Page<BackupRecord> result = recordMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<BackupRecord>().orderByDesc(BackupRecord::getStartedAt));
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
            result.getTotal(), page, size);
    }

    @Override
    public PersistenceStatusVO status() {
        PersistenceSetting setting = settingService.getPersistence();
        Path root = Path.of(setting.storagePath()).toAbsolutePath().normalize();
        List<TableStatVO> tables = statMapper.selectTableStats(BackupTables.ALL);
        long size = tables.stream().mapToLong(stat -> stat.getSizeBytes() == null ? 0 : stat.getSizeBytes()).sum();
        long rows = tables.stream().mapToLong(stat -> stat.getRowEstimate() == null ? 0 : stat.getRowEstimate()).sum();
        boolean writable = Files.isDirectory(root) && Files.isWritable(root);
        BackupRecord running = recordMapper.selectRunning();
        BackupRecord last = recordMapper.selectOne(new LambdaQueryWrapper<BackupRecord>()
            .eq(BackupRecord::getStatus, "SUCCESS").orderByDesc(BackupRecord::getFinishedAt).last("LIMIT 1"));
        String nextRun = null;
        if (setting.enabled() && setting.autoBackup() && autoBackupEnabled) {
            try {
                nextRun = CronExpression.parse(setting.backupCron()).next(LocalDateTime.now()).format(SQL_TIME);
            } catch (RuntimeException ignored) {
                // 配置入库前已校验，这里只做兜底：无法计算就不展示下次时间
            }
        }
        return PersistenceStatusVO.of(setting.enabled(), root.toString(), writable, size, rows,
            chunkMapper.selectCount(null), tables, last == null ? null : toVO(last), nextRun,
            autoBackupEnabled, running == null ? null : running.getId());
    }

    @Override
    public BackupVerifyVO verify(long id) {
        BackupRecord record = require(id);
        if (!"SUCCESS".equals(record.getStatus())) {
            return BackupVerifyVO.fail("只有成功的备份才能校验");
        }
        Path archive = resolveArchive(id);
        if (!Files.isRegularFile(archive)) {
            return BackupVerifyVO.fail("归档文件不存在");
        }
        try {
            if (!record.getChecksum().equals(sha256(archive))) {
                return BackupVerifyVO.fail("归档整体校验和不一致，文件可能已被改动");
            }
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                JsonNode manifest = mapper.readTree(readEntry(zip, "manifest.json"));
                ArrayNode tables = (ArrayNode) manifest.get("tables");
                for (JsonNode table : tables) {
                    String name = table.get("name").asText();
                    ZipEntry entry = zip.getEntry("data/" + name + ".jsonl");
                    if (entry == null || !table.get("sha256").asText().equals(sha256(readEntry(zip, entry)))) {
                        return BackupVerifyVO.fail("表数据校验失败：" + name);
                    }
                }
            }
            return BackupVerifyVO.ok();
        } catch (Exception exception) {
            return BackupVerifyVO.fail("无法读取归档：" + trimMessage(exception.getMessage()));
        }
    }

    @Override
    public Path resolveArchive(long id) {
        BackupRecord record = require(id);
        String name = record.getFileName();
        if (name == null || !ARCHIVE_NAME.matcher(name).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "归档文件名不合法");
        }
        Path root = Path.of(settingService.getPersistence().storagePath()).toAbsolutePath().normalize();
        Path target = root.resolve(name).normalize();
        if (!root.equals(target.getParent())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "归档路径不合法");
        }
        return target;
    }

    @Override
    public String fileName(long id) {
        return require(id).getFileName();
    }

    @Override
    @Transactional
    public void delete(long id) {
        BackupRecord record = require(id);
        if ("RUNNING".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "备份进行中，不能删除");
        }
        Path archive = resolveArchive(id);
        deleteQuietly(archive);
        recordMapper.deleteById(id);
    }

    /** 保留期清理：只删本应用生成、未标记 keep_forever、且已过期的归档。 */
    public void cleanExpired() {
        PersistenceSetting setting = settingService.getPersistence();
        Path root = Path.of(setting.storagePath()).toAbsolutePath().normalize();
        LocalDateTime before = LocalDateTime.now().minusDays(setting.retentionDays());
        for (BackupRecord record : recordMapper.selectExpired(before)) {
            String name = record.getFileName();
            if (name == null || !ARCHIVE_NAME.matcher(name).matches()) {
                log.warn("跳过非本应用归档：{}", name);
                continue;
            }
            Path file = root.resolve(name).normalize();
            if (!root.equals(file.getParent())) {
                log.warn("跳过目录外归档：{}", name);
                continue;
            }
            deleteQuietly(file);
            recordMapper.deleteById(record.getId());
            log.info("已清理过期归档：{}", name);
        }
    }

    private BackupRecord newRecord(String trigger, PersistenceSetting setting, boolean keepForever) {
        BackupRecord record = new BackupRecord();
        String suffix = switch (trigger) {
            case "AUTO" -> "auto";
            case "PRE_RESTORE" -> "pre-restore";
            default -> "manual";
        };
        String base = "aireview-" + LocalDateTime.now().format(STAMP) + "-" + suffix;
        Path root = Path.of(setting.storagePath()).toAbsolutePath().normalize();
        String name = base + ".zip";
        for (int attempt = 1; Files.exists(root.resolve(name)) && attempt < 100; attempt++) {
            name = base + "-" + attempt + ".zip";
        }
        record.setFileName(name);
        record.setTriggerType(trigger);
        record.setStatus("RUNNING");
        record.setSizeBytes(0L);
        record.setChecksum("");
        record.setTableCount(0);
        record.setRowCount(0L);
        record.setKeepForever(keepForever ? 1 : 0);
        record.setStartedAt(LocalDateTime.now());
        // 文件名含秒级时间戳，极端并发下由 uk_backup_file 兜底
        if (setting == null || setting.storagePath() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "备份目录未配置");
        }
        return record;
    }

    private BackupRecord require(long id) {
        BackupRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "备份记录不存在");
        }
        return record;
    }

    private BackupRecordVO toVO(BackupRecord record) {
        return BackupRecordVO.of(record, null, BackupTables.ALL.size());
    }

    private byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("归档缺少 " + name);
        }
        return readEntry(zip, entry);
    }

    private String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private MessageDigest digest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("删除临时/过期文件失败：{} ({})", file, exception.getMessage());
        }
    }

    private String trimMessage(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record TableInfo(String name, int rows, String sha256) {
    }

    private record BackupSummary(int tables, long rows) {
    }
}
