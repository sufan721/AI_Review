package com.aireview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 附件存储后端设置（对应 instance_setting.STORAGE）。
 *
 * <p>两种模式仅字节落点不同：
 * <ul>
 *   <li>{@link Mode#LOCAL_FS}：新附件写入服务器文件系统，默认且推荐；</li>
 *   <li>{@link Mode#DATABASE}：新附件二进制直接写入应用数据库（attachment_blob）。</li>
 * </ul>
 * 切换只影响之后的新附件：每条 attachment 记录自己的 storage_mode，读取时按行分派。
 */
public record AttachmentStorageSetting(
    Mode mode,
    String localDir,
    int maxSizeMb) {

    public enum Mode {
        LOCAL_FS,
        DATABASE
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认本地文件系统，对应「新实例默认使用这种方式 / 推荐」。 */
    public static AttachmentStorageSetting defaults() {
        return new AttachmentStorageSetting(Mode.LOCAL_FS, "./data/attachments", 20);
    }

    public static AttachmentStorageSetting fromJson(String value) {
        AttachmentStorageSetting defaults = defaults();
        if (value == null || value.isBlank()) {
            return defaults;
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            if (!node.isObject()) {
                return defaults;
            }
            Mode mode = defaults.mode();
            if (node.has("mode") && node.get("mode").isTextual()) {
                try {
                    mode = Mode.valueOf(node.get("mode").asText().trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignore) {
                    mode = defaults.mode();
                }
            }
            String dir = node.has("localDir") && node.get("localDir").isTextual()
                ? node.get("localDir").asText() : defaults.localDir();
            int max = node.has("maxSizeMb") && node.get("maxSizeMb").isNumber()
                ? node.get("maxSizeMb").asInt() : defaults.maxSizeMb();
            return new AttachmentStorageSetting(mode, dir, max);
        } catch (Exception exception) {
            return defaults;
        }
    }

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
            return "{\"mode\":\"LOCAL_FS\",\"localDir\":\"./data/attachments\",\"maxSizeMb\":20}";
        }
    }

    /** 校验并规范化：目录转绝对路径，大小夹到 [1,200]，非法值抛 IllegalArgumentException。 */
    public AttachmentStorageSetting normalize() {
        if (mode == null) {
            throw new IllegalArgumentException("存储方式不能为空");
        }
        String dir = localDir == null || localDir.isBlank() ? defaults().localDir() : localDir.trim();
        String resolved = java.nio.file.Path.of(dir).toAbsolutePath().normalize().toString();
        if (maxSizeMb < 1 || maxSizeMb > 200) {
            throw new IllegalArgumentException("单附件大小上限需在 1-200 MB 之间");
        }
        return new AttachmentStorageSetting(mode, resolved, maxSizeMb);
    }
}
