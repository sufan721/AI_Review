package com.aireview.entity;

/**
 * 知识库成员角色。第一版只区分三类：
 * <ul>
 *   <li>{@code OWNER} 拥有者：管理知识库、成员与资料；</li>
 *   <li>{@code EDITOR} 编辑成员：上传、修改、删除资料；</li>
 *   <li>{@code VIEWER} 只读成员：只能查看资料与提问。</li>
 * </ul>
 * 数据库中 {@code knowledge_base_member.member_role} 以字符串 {@code name()} 存储。
 */
public enum MemberRole {
    OWNER,
    EDITOR,
    VIEWER
}
