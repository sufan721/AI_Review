package com.aireview.service;

import com.aireview.dto.PatCreatedVO;
import com.aireview.dto.PatVO;
import java.time.Duration;
import java.util.List;

/**
 * 个人访问令牌服务（对齐 memos PersonalAccessTokenService）。
 *
 * <p>仅存令牌摘要（SHA-256），原始值只在创建时返回一次。
 * 命中后权限等同该用户，可按应用分条、可吊销。
 */
public interface PersonalAccessTokenService {

    /** 签发一条 PAT；返回包含原始令牌的视图，客户端必须立即保存。 */
    PatCreatedVO issue(Long userId, String description, Duration ttl);

    /** 校验原始令牌：返回对应 userId；无效（不存在/过期/用户禁用）返回 null。 */
    Long verify(String rawToken);

    /** 列出当前用户的所有 PAT（不含 token_hash）。 */
    List<PatVO> list(Long userId);

    /** 吊销指定 PAT；仅本人可吊销。返回是否命中。 */
    boolean revoke(Long userId, Long id);
}
