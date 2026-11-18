package com.aireview.dto;

/**
 * 实例级通用设置（对齐 memos store.InstanceSetting GENERAL 项）。
 *
 * <p>读取时缺失的 key 走默认值：注册开放、密码登录开放，避免冷启动把实例锁死。
 */
public record GeneralSetting(boolean disallowUserRegistration, boolean disallowPasswordAuth) {

    /** 未写入 instance_setting 时的兜底值：两项都开放。 */
    public static GeneralSetting defaults() {
        return new GeneralSetting(false, false);
    }

    /** JSON value -> record，解析失败或字段缺失时回退默认值。 */
    public static GeneralSetting fromJson(String value) {
        GeneralSetting defaults = defaults();
        if (value == null || value.isBlank()) {
            return defaults;
        }
        String trimmed = value.trim();
        boolean disallowRegistration = defaults.disallowUserRegistration;
        boolean disallowPassword = defaults.disallowPasswordAuth;
        // 轻量解析：避免为两个布尔字段引入完整 JSON 反序列化器
        disallowRegistration = readBoolean(trimmed, "disallowUserRegistration", disallowRegistration);
        disallowPassword = readBoolean(trimmed, "disallowPasswordAuth", disallowPassword);
        return new GeneralSetting(disallowRegistration, disallowPassword);
    }

    public String toJson() {
        return "{\"disallowUserRegistration\":"
            + disallowUserRegistration
            + ",\"disallowPasswordAuth\":"
            + disallowPasswordAuth
            + "}";
    }

    private static boolean readBoolean(String json, String field, boolean fallback) {
        int idx = json.indexOf(field);
        if (idx < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', idx + field.length());
        if (colon < 0) {
            return fallback;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return fallback;
        }
        char c = json.charAt(start);
        return c == 't' || c == 'T';
    }
}
