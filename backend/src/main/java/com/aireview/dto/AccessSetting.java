package com.aireview.dto;

/**
 * 实例访问模式（对齐 memos store.InstanceSetting ACCESS 项）。
 *
 * <ul>
 *   <li>PUBLIC：匿名可访问公开资源接口（公开笔记/文档列表与详情）。</li>
 *   <li>PRIVATE：所有接口除引导接口外都必须登录。</li>
 * </ul>
 */
public record AccessSetting(Mode mode) {

    public enum Mode {
        PUBLIC,
        PRIVATE
    }

    /** 默认 PRIVATE：未显式声明时把实例视为私有，符合「最小暴露」原则。 */
    public static AccessSetting defaults() {
        return new AccessSetting(Mode.PRIVATE);
    }

    public static AccessSetting fromJson(String value) {
        if (value == null || value.isBlank()) {
            return defaults();
        }
        String trimmed = value.trim();
        // 支持 {"mode":"PUBLIC"} 与裸字符串 "PUBLIC" 两种形态
        String mode = readStringField(trimmed, "mode");
        if (mode == null || mode.isBlank()) {
            return defaults();
        }
        try {
            return new AccessSetting(Mode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ignore) {
            return defaults();
        }
    }

    public String toJson() {
        return "{\"mode\":\"" + mode.name() + "\"}";
    }

    public boolean isPublic() {
        return mode == Mode.PUBLIC;
    }

    private static String readStringField(String json, String field) {
        int idx = json.indexOf(field);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + field.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }
}
