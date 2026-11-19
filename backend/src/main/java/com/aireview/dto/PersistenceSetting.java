package com.aireview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.scheduling.support.CronExpression;

/**
 * 数据快照设置（instance_setting 的 PERSISTENCE 项）。
 *
 * <p>与 {@link GeneralSetting} 一样在后端定义 schema 与默认值，前端不再手拼 JSON。
 * 这里的 {@code storagePath} 是「备份归档根目录」，不是数据库数据目录——运行中的
 * MySQL datadir 不可能由 Web 配置改写。
 *
 * @param enabled       是否启用快照能力（关闭只停止自动备份，业务数据仍由数据库持久化）
 * @param storagePath   归档根目录
 * @param autoBackup    是否按 cron 自动生成快照
 * @param backupCron    Spring 六段 cron（秒 分 时 日 月 周）
 * @param retentionDays 自动归档保留天数
 */
public record PersistenceSetting(
    boolean enabled,
    String storagePath,
    boolean autoBackup,
    String backupCron,
    int retentionDays) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 冷启动兜底：默认启用快照、每天凌晨 3 点、保留 30 天，自动备份默认关闭。 */
    public static PersistenceSetting defaults() {
        return new PersistenceSetting(true, "./data/backups", false, "0 0 3 * * *", 30);
    }

    /** JSON 缺失或字段损坏时逐字段回退默认值，绝不让坏配置阻断管理页。 */
    public static PersistenceSetting fromJson(String value) {
        PersistenceSetting defaults = defaults();
        if (value == null || value.isBlank()) {
            return defaults;
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            if (!node.isObject()) {
                return defaults;
            }
            boolean enabled = bool(node, "enabled", defaults.enabled);
            String path = text(node, "storagePath", defaults.storagePath);
            boolean autoBackup = bool(node, "autoBackup", defaults.autoBackup);
            String cron = text(node, "backupCron", defaults.backupCron);
            int days = node.has("retentionDays") && node.get("retentionDays").isNumber()
                ? node.get("retentionDays").asInt() : defaults.retentionDays;
            return new PersistenceSetting(enabled, path, autoBackup, cron, days);
        } catch (Exception exception) {
            return defaults;
        }
    }

    /** 供服务层判断已存配置是否损坏；损坏时回退默认值并打 WARN，而不再静默吞掉。 */
    public static boolean isValidJson(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            return MAPPER.readTree(value).isObject();
        } catch (Exception exception) {
            return false;
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception exception) {
            // record 只有基础类型，理论上不会发生；保底写死默认值以免写入空值
            return "{\"enabled\":true,\"storagePath\":\"./data/backups\",\"autoBackup\":false,"
                + "\"backupCron\":\"0 0 3 * * *\",\"retentionDays\":30}";
        }
    }

    /** 规范化并校验；非法输入抛 IllegalArgumentException，由调用方转 400。 */
    public PersistenceSetting normalize() {
        String path = storagePath == null || storagePath.isBlank()
            ? defaults().storagePath : storagePath.trim();
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        if (!CronExpression.isValidExpression(backupCron == null ? "" : backupCron.trim())) {
            throw new IllegalArgumentException("备份周期（cron）不合法");
        }
        int days = retentionDays < 1 || retentionDays > 3650 ? defaults().retentionDays : retentionDays;
        if (days != retentionDays) {
            throw new IllegalArgumentException("保留天数需在 1-3650 之间");
        }
        return new PersistenceSetting(enabled, resolved.toString(), autoBackup, backupCron.trim(), days);
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        return node.has(field) && node.get(field).isBoolean() ? node.get(field).asBoolean() : fallback;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).asText() : fallback;
    }
}
