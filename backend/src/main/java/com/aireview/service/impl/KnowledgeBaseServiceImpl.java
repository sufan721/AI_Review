package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.KnowledgeBaseCreateRequest;
import com.aireview.dto.KnowledgeBaseMemberVO;
import com.aireview.dto.KnowledgeBaseResourceVO;
import com.aireview.dto.KnowledgeBaseUpdateRequest;
import com.aireview.dto.KnowledgeBaseVO;
import com.aireview.dto.MemberAddRequest;
import com.aireview.dto.MemberRoleUpdateRequest;
import com.aireview.dto.ResourceAddRequest;
import com.aireview.entity.Document;
import com.aireview.entity.KnowledgeBase;
import com.aireview.entity.KnowledgeBaseMember;
import com.aireview.entity.KnowledgeBaseResource;
import com.aireview.entity.MemberRole;
import com.aireview.entity.Note;
import com.aireview.entity.User;
import com.aireview.index.ResourceType;
import com.aireview.mapper.DocumentMapper;
import com.aireview.mapper.KnowledgeBaseMapper;
import com.aireview.mapper.KnowledgeBaseMemberMapper;
import com.aireview.mapper.KnowledgeBaseResourceMapper;
import com.aireview.mapper.NoteMapper;
import com.aireview.mapper.UserMapper;
import com.aireview.service.KnowledgeBaseService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库业务实现。
 *
 * <p>授权原则：只看 {@code knowledge_base_member}，<b>不参考全局角色</b>——即使调用方拥有
 * {@code ROLE_ADMIN}，也必须显式成为知识库成员后才能操作其中的资料。
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final Set<MemberRole> ALL_ROLES = Set.of(MemberRole.OWNER, MemberRole.EDITOR, MemberRole.VIEWER);
    private static final Set<MemberRole> EDITOR_OR_OWNER = Set.of(MemberRole.OWNER, MemberRole.EDITOR);
    private static final Set<MemberRole> OWNER_ONLY = Set.of(MemberRole.OWNER);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseMemberMapper memberMapper;
    private final KnowledgeBaseResourceMapper resourceMapper;
    private final NoteMapper noteMapper;
    private final DocumentMapper documentMapper;
    private final UserMapper userMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
        KnowledgeBaseMemberMapper memberMapper, KnowledgeBaseResourceMapper resourceMapper,
        NoteMapper noteMapper, DocumentMapper documentMapper, UserMapper userMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.memberMapper = memberMapper;
        this.resourceMapper = resourceMapper;
        this.noteMapper = noteMapper;
        this.documentMapper = documentMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest request) {
        Long userId = UserContext.require();
        KnowledgeBase kb = new KnowledgeBase();
        kb.setOwnerId(userId);
        kb.setName(request.name().trim());
        kb.setDescription(blankToNull(request.description()));
        knowledgeBaseMapper.insert(kb);
        insertMember(kb.getId(), userId, MemberRole.OWNER);
        return toVO(knowledgeBaseMapper.selectById(kb.getId()), MemberRole.OWNER.name());
    }

    @Override
    public List<KnowledgeBaseVO> list() {
        Long userId = UserContext.require();
        List<KnowledgeBaseMember> members = memberMapper.selectList(
            Wrappers.<KnowledgeBaseMember>lambdaQuery().eq(KnowledgeBaseMember::getUserId, userId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> kbIds = members.stream().map(KnowledgeBaseMember::getKnowledgeBaseId).distinct().toList();
        Map<Long, KnowledgeBase> kbMap = knowledgeBaseMapper.selectBatchIds(kbIds).stream()
            .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));
        return members.stream()
            .map(member -> kbMap.get(member.getKnowledgeBaseId()))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(KnowledgeBase::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(kb -> toVO(kb, roleOf(kb.getId(), members)))
            .toList();
    }

    @Override
    public KnowledgeBaseVO detail(Long id) {
        MemberRole role = requireMember(id, ALL_ROLES);
        return toVO(loadKnowledgeBase(id), role.name());
    }

    @Override
    public KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateRequest request) {
        requireMember(id, OWNER_ONLY);
        KnowledgeBase kb = loadKnowledgeBase(id);
        kb.setName(request.name().trim());
        kb.setDescription(blankToNull(request.description()));
        kb.setCreatedAt(null);
        kb.setUpdatedAt(null);
        knowledgeBaseMapper.updateById(kb);
        return toVO(knowledgeBaseMapper.selectById(id), MemberRole.OWNER.name());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireMember(id, OWNER_ONLY);
        memberMapper.delete(Wrappers.<KnowledgeBaseMember>lambdaQuery()
            .eq(KnowledgeBaseMember::getKnowledgeBaseId, id));
        resourceMapper.delete(Wrappers.<KnowledgeBaseResource>lambdaQuery()
            .eq(KnowledgeBaseResource::getKnowledgeBaseId, id));
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public List<KnowledgeBaseMemberVO> listMembers(Long kbId) {
        requireMember(kbId, ALL_ROLES);
        List<KnowledgeBaseMember> members = memberMapper.selectList(
            Wrappers.<KnowledgeBaseMember>lambdaQuery().eq(KnowledgeBaseMember::getKnowledgeBaseId, kbId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = members.stream().map(KnowledgeBaseMember::getUserId).toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
            .map(member -> {
                User user = userMap.get(member.getUserId());
                String username = user == null ? null : user.getUsername();
                String nickname = user == null ? null : user.getNickname();
                return new KnowledgeBaseMemberVO(member.getUserId(), username, nickname,
                    member.getMemberRole(), member.getCreatedAt());
            })
            .toList();
    }

    @Override
    @Transactional
    public void addMember(Long kbId, MemberAddRequest request) {
        requireMember(kbId, OWNER_ONLY);
        MemberRole role = parseRole(request.memberRole());
        if (userMapper.selectById(request.userId()) == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户不存在");
        }
        if (memberExists(kbId, request.userId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户已是知识库成员");
        }
        insertMember(kbId, request.userId(), role);
    }

    @Override
    @Transactional
    public void updateMemberRole(Long kbId, Long userId, MemberRoleUpdateRequest request) {
        requireMember(kbId, OWNER_ONLY);
        MemberRole newRole = parseRole(request.memberRole());
        KnowledgeBaseMember member = loadMember(kbId, userId);
        if (MemberRole.OWNER.name().equals(member.getMemberRole()) && newRole != MemberRole.OWNER) {
            ensureNotLastOwner(kbId);
        }
        member.setMemberRole(newRole.name());
        memberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void removeMember(Long kbId, Long userId) {
        requireMember(kbId, OWNER_ONLY);
        KnowledgeBaseMember member = loadMember(kbId, userId);
        if (MemberRole.OWNER.name().equals(member.getMemberRole())) {
            ensureNotLastOwner(kbId);
        }
        memberMapper.deleteById(member.getId());
    }

    @Override
    public List<KnowledgeBaseResourceVO> listResources(Long kbId) {
        requireMember(kbId, ALL_ROLES);
        List<KnowledgeBaseResource> resources = resourceMapper.selectList(
            Wrappers.<KnowledgeBaseResource>lambdaQuery().eq(KnowledgeBaseResource::getKnowledgeBaseId, kbId));
        if (resources.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = resources.stream()
            .filter(r -> ResourceType.NOTE.name().equals(r.getResourceType()))
            .map(KnowledgeBaseResource::getResourceId).toList();
        List<Long> documentIds = resources.stream()
            .filter(r -> ResourceType.DOCUMENT.name().equals(r.getResourceType()))
            .map(KnowledgeBaseResource::getResourceId).toList();
        Map<Long, Note> noteMap = noteIds.isEmpty() ? Map.of() : noteMapper.selectBatchIds(noteIds).stream()
            .collect(Collectors.toMap(Note::getId, Function.identity()));
        Map<Long, Document> documentMap = documentIds.isEmpty() ? Map.of() : documentMapper.selectBatchIds(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, Function.identity()));
        return resources.stream()
            .map(resource -> toResourceVO(resource, noteMap, documentMap))
            .toList();
    }

    @Override
    @Transactional
    public void addResource(Long kbId, ResourceAddRequest request) {
        requireMember(kbId, EDITOR_OR_OWNER);
        ResourceType type = parseResourceType(request.resourceType());
        // 第一版限制：只能把「当前用户自己拥有」的资料加入知识库，避免越权共享他人资料。
        validateOwnership(type, request.resourceId(), UserContext.require());
        if (resourceExists(kbId, type, request.resourceId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该资料已在知识库中");
        }
        KnowledgeBaseResource resource = new KnowledgeBaseResource();
        resource.setKnowledgeBaseId(kbId);
        resource.setResourceType(type.name());
        resource.setResourceId(request.resourceId());
        resourceMapper.insert(resource);
    }

    @Override
    @Transactional
    public void removeResource(Long kbId, String resourceType, Long resourceId) {
        requireMember(kbId, EDITOR_OR_OWNER);
        ResourceType type = parseResourceType(resourceType);
        resourceMapper.delete(Wrappers.<KnowledgeBaseResource>lambdaQuery()
            .eq(KnowledgeBaseResource::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseResource::getResourceType, type.name())
            .eq(KnowledgeBaseResource::getResourceId, resourceId));
    }

    @Override
    @Transactional
    public void deleteResourceAssociations(ResourceType resourceType, long resourceId) {
        resourceMapper.delete(Wrappers.<KnowledgeBaseResource>lambdaQuery()
            .eq(KnowledgeBaseResource::getResourceType, resourceType.name())
            .eq(KnowledgeBaseResource::getResourceId, resourceId));
    }

    private MemberRole requireMember(Long kbId, Set<MemberRole> allowed) {
        Long userId = UserContext.require();
        KnowledgeBaseMember member = memberMapper.selectOne(Wrappers.<KnowledgeBaseMember>lambdaQuery()
            .eq(KnowledgeBaseMember::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseMember::getUserId, userId));
        if (member == null) {
            // 非成员与不存在的知识库统一 403，避免通过状态码探测知识库是否存在
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        MemberRole role = parseRole(member.getMemberRole());
        if (!allowed.contains(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return role;
    }

    private KnowledgeBase loadKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return kb;
    }

    private KnowledgeBaseMember loadMember(Long kbId, Long userId) {
        KnowledgeBaseMember member = memberMapper.selectOne(Wrappers.<KnowledgeBaseMember>lambdaQuery()
            .eq(KnowledgeBaseMember::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该用户不是知识库成员");
        }
        return member;
    }

    private boolean memberExists(Long kbId, Long userId) {
        return memberMapper.selectCount(Wrappers.<KnowledgeBaseMember>lambdaQuery()
            .eq(KnowledgeBaseMember::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseMember::getUserId, userId)) > 0;
    }

    private boolean resourceExists(Long kbId, ResourceType type, Long resourceId) {
        return resourceMapper.selectCount(Wrappers.<KnowledgeBaseResource>lambdaQuery()
            .eq(KnowledgeBaseResource::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseResource::getResourceType, type.name())
            .eq(KnowledgeBaseResource::getResourceId, resourceId)) > 0;
    }

    private void ensureNotLastOwner(Long kbId) {
        Long ownerCount = memberMapper.selectCount(Wrappers.<KnowledgeBaseMember>lambdaQuery()
            .eq(KnowledgeBaseMember::getKnowledgeBaseId, kbId)
            .eq(KnowledgeBaseMember::getMemberRole, MemberRole.OWNER.name()));
        if (ownerCount != null && ownerCount <= 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能移除或降级知识库最后一个拥有者");
        }
    }

    private void insertMember(Long kbId, Long userId, MemberRole role) {
        KnowledgeBaseMember member = new KnowledgeBaseMember();
        member.setKnowledgeBaseId(kbId);
        member.setUserId(userId);
        member.setMemberRole(role.name());
        memberMapper.insert(member);
    }

    private void validateOwnership(ResourceType type, Long resourceId, Long userId) {
        boolean owned;
        if (type == ResourceType.NOTE) {
            owned = noteMapper.selectCount(Wrappers.<Note>lambdaQuery()
                .eq(Note::getId, resourceId).eq(Note::getUserId, userId)) > 0;
        } else {
            owned = documentMapper.selectCount(Wrappers.<Document>lambdaQuery()
                .eq(Document::getId, resourceId).eq(Document::getUserId, userId)) > 0;
        }
        if (!owned) {
            // 与笔记/文件详情一致：他人资源与不存在资源统一 403
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private KnowledgeBaseResourceVO toResourceVO(KnowledgeBaseResource resource,
        Map<Long, Note> noteMap, Map<Long, Document> documentMap) {
        String name;
        String indexStatus;
        if (ResourceType.NOTE.name().equals(resource.getResourceType())) {
            Note note = noteMap.get(resource.getResourceId());
            name = note == null ? null : note.getTitle();
            indexStatus = note == null ? null : note.getIndexStatus();
        } else {
            Document document = documentMap.get(resource.getResourceId());
            name = document == null ? null : document.getFileName();
            indexStatus = document == null ? null : document.getIndexStatus();
        }
        return new KnowledgeBaseResourceVO(resource.getResourceId(), resource.getResourceType(),
            name, indexStatus, resource.getCreatedAt());
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb, String memberRole) {
        return new KnowledgeBaseVO(kb.getId(), kb.getOwnerId(), kb.getName(), kb.getDescription(),
            memberRole, kb.getCreatedAt(), kb.getUpdatedAt());
    }

    private static String roleOf(Long kbId, List<KnowledgeBaseMember> members) {
        return members.stream()
            .filter(member -> member.getKnowledgeBaseId().equals(kbId))
            .map(KnowledgeBaseMember::getMemberRole)
            .findFirst()
            .orElse(null);
    }

    private static MemberRole parseRole(String value) {
        try {
            return MemberRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的成员角色：" + value);
        }
    }

    private static ResourceType parseResourceType(String value) {
        try {
            return ResourceType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的资源类型：" + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
