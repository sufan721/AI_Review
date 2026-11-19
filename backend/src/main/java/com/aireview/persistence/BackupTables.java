package com.aireview.persistence;

import java.util.List;

/**
 * 参与快照的业务表白名单。
 *
 * <p>顺序同时是恢复时的写入顺序（父表在前），删除时逆序执行。
 * {@code backup_record} 自身故意不在其中：恢复历史不应被快照覆盖。
 * 新增业务表时，必须同步本常量与 BackupDumpMapper.xml 的三组语句，
 * 单测会断言两者一致，防止漏写。
 */
public final class BackupTables {

    public static final List<String> ALL = List.of(
        "user",
        "sys_role",
        "sys_permission",
        "sys_user_role",
        "sys_role_permission",
        "sys_user_sso_binding",
        "sys_login_log",
        "note",
        "document",
        "document_chunk",
        "knowledge_base",
        "knowledge_base_member",
        "knowledge_base_resource",
        "user_setting",
        "personal_access_token",
        "user_identity",
        "identity_provider",
        "instance_setting",
        "attachment",
        "attachment_blob");

    private BackupTables() {
    }
}
