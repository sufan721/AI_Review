package com.aireview.service;

import com.aireview.dto.KnowledgeBaseCreateRequest;
import com.aireview.dto.KnowledgeBaseMemberVO;
import com.aireview.dto.KnowledgeBaseResourceVO;
import com.aireview.dto.KnowledgeBaseUpdateRequest;
import com.aireview.dto.KnowledgeBaseVO;
import com.aireview.dto.MemberAddRequest;
import com.aireview.dto.MemberRoleUpdateRequest;
import com.aireview.dto.ResourceAddRequest;
import com.aireview.index.ResourceType;
import java.util.List;

/** 知识库、成员与资料关联的业务逻辑。授权一律只看 {@code knowledge_base_member}，不依赖全局角色。 */
public interface KnowledgeBaseService {

    KnowledgeBaseVO create(KnowledgeBaseCreateRequest request);

    List<KnowledgeBaseVO> list();

    KnowledgeBaseVO detail(Long id);

    KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);

    List<KnowledgeBaseMemberVO> listMembers(Long kbId);

    void addMember(Long kbId, MemberAddRequest request);

    void updateMemberRole(Long kbId, Long userId, MemberRoleUpdateRequest request);

    void removeMember(Long kbId, Long userId);

    List<KnowledgeBaseResourceVO> listResources(Long kbId);

    void addResource(Long kbId, ResourceAddRequest request);

    void removeResource(Long kbId, String resourceType, Long resourceId);

    /** 删除指定资料的所有知识库关联（幂等）。由笔记/文件删除流程调用。 */
    void deleteResourceAssociations(ResourceType resourceType, long resourceId);
}
