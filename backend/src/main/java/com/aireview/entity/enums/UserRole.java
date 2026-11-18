package com.aireview.entity.enums;

/**
 * 实例级用户角色，对齐 memos 的 User.Role。
 * 与现有 RBAC（ROLE_ADMIN/ROLE_USER）并存；memos 语义用此枚举。
 */
public enum UserRole {
    ADMIN,
    USER
}
