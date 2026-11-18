package com.aireview.dto;

/**
 * 实例级通用设置（对齐 memos store.InstanceSetting GENERAL 项）。
 *
 * <p>读取时缺失的 key 走默认值：注册开放、密码登录开放、资料编辑开放，避免冷启动把实例锁死。
 *
 * <ul>
 *   <li>{@code disallowUserRegistration}：禁用用户注册。</li>
 *   <li>{@code disallowPasswordAuth}：禁用密码登录（强制走身份提供商）。</li>
 *   <li>{@code forbidChangeUsername}：禁止用户修改登录用户名（admin 代改不受限）。</li>
 *   <li>{@code forbidChangeNickname}：禁止用户修改显示昵称（admin 代改不受限）。</li>
 * </ul>
 */
public record GeneralSetting(
    boolean disallowUserRegistration,
    boolean disallowPasswordAuth,
    boolean forbidChangeUsername,
    boolean forbidChangeNickname) {

    /** 未写入 instance_setting 时的兜底值：四项全部开放。 */
    public static GeneralSetting defaults() {
        return new GeneralSetting(false, false, false, false);
    }

    /** JSON value -> record，解析失败或字段缺失时回退默认值。 */
    public static GeneralSetting fromJson(String value) {
        GeneralSetting defaults = defaults();
        if (value == null || value.isBlank()) {
            return defaults;
        }
        String trimmed = value.trim();
        // 轻量解析：避免为几个布尔字段引入完整 JSON 反序列化器
        boolean disallowRegistration = readBoolean(trimmed, "disallowUserRegistration", defaults.disallowUserRegistration);
        boolean disallowPassword = readBoolean(trimmed, "disallowPasswordAuth", defaults.disallowPasswordAuth);
        boolean forbidUsername = readBoolean(trimmed, "forbidChangeUsername", defaults.forbidChangeUsername);
        boolean forbidNickname = readBoolean(trimmed, "forbidChangeNickname", defaults.forbidChangeNickname);
        return new GeneralSetting(disallowRegistration, disallowPassword, forbidUsername, forbidNickname);
    }

    public String toJson() {
        return "{\"disallowUserRegistration\":" + disallowUserRegistration
            + ",\"disallowPasswordAuth\":" + disallowPasswordAuth
            + ",\"forbidChangeUsername\":" + forbidChangeUsername
            + ",\"forbidChangeNickname\":" + forbidChangeNickname
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
