package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.PageResult;
import com.aireview.common.ResultCode;
import com.aireview.dto.NoteCreateRequest;
import com.aireview.dto.NoteQueryRequest;
import com.aireview.dto.NoteSummaryVO;
import com.aireview.dto.NoteUpdateRequest;
import com.aireview.dto.NoteVO;
import com.aireview.entity.Note;
import com.aireview.index.IndexingService;
import com.aireview.index.ResourceType;
import com.aireview.mapper.NoteMapper;
import com.aireview.security.OwnershipService;
import com.aireview.service.KnowledgeBaseService;
import com.aireview.service.NoteService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteServiceImpl implements NoteService {
    /** 正文变更后待重新索引，实际索引任务在索引功能中投递。 */
    private static final String INDEX_STATUS_PENDING = "PENDING";

    private final NoteMapper noteMapper;
    private final IndexingService indexingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final OwnershipService ownershipService;

    public NoteServiceImpl(NoteMapper noteMapper, IndexingService indexingService,
        KnowledgeBaseService knowledgeBaseService, OwnershipService ownershipService) {
        this.noteMapper = noteMapper;
        this.indexingService = indexingService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ownershipService = ownershipService;
    }

    @Override
    public PageResult<NoteSummaryVO> list(NoteQueryRequest query) {
        long pageNo = query.pageOrDefault();
        long size = query.sizeOrDefault();
        LambdaQueryWrapper<Note> wrapper = Wrappers.<Note>lambdaQuery()
            .eq(Note::getUserId, UserContext.require())
            .eq(query.isArchived() != null, Note::getIsArchived, query.isArchived())
            .eq(query.isPinned() != null, Note::getIsPinned, query.isPinned())
            // 置顶优先，其次按最近更新
            .orderByDesc(Note::getIsPinned)
            .orderByDesc(Note::getUpdatedAt);

        Page<Note> result = noteMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<NoteSummaryVO> records = result.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(records, result.getTotal(), pageNo, size);
    }

    @Override
    public NoteVO create(NoteCreateRequest request) {
        Note note = new Note();
        note.setUserId(UserContext.require());
        note.setTitle(normalizeTitle(request.title()));
        note.setContent(request.content() == null ? "" : request.content());
        noteMapper.insert(note);
        indexingService.schedule(ResourceType.NOTE, note.getId());
        // 回读以拿到数据库生成的时间戳与默认值
        return toVO(noteMapper.selectById(note.getId()));
    }

    @Override
    public NoteVO detail(Long id) {
        return toVO(loadOwned(id));
    }

    @Override
    public NoteVO update(Long id, NoteUpdateRequest request) {
        Note note = loadOwned(id);
        String content = request.content() == null ? "" : request.content();
        boolean contentChanged = !Objects.equals(note.getContent(), content);
        note.setTitle(normalizeTitle(request.title()));
        if (contentChanged) {
            note.setContent(content);
            note.setContentVersion(note.getContentVersion() + 1);
            note.setIndexStatus(INDEX_STATUS_PENDING);
        }
        // 置空时间戳，让数据库的 ON UPDATE CURRENT_TIMESTAMP 生效而不是写回旧值
        note.setCreatedAt(null);
        note.setUpdatedAt(null);
        noteMapper.updateById(note);
        if (contentChanged) {
            indexingService.schedule(ResourceType.NOTE, id);
        }
        return toVO(noteMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Note note = loadOwned(id);
        noteMapper.deleteById(note.getId());
        knowledgeBaseService.deleteResourceAssociations(ResourceType.NOTE, id);
        indexingService.deleteResource(ResourceType.NOTE, id);
    }

    @Override
    public NoteVO reindex(Long id) {
        loadOwned(id);
        indexingService.reindex(ResourceType.NOTE, id);
        return toVO(noteMapper.selectById(id));
    }

    @Override
    public NoteVO togglePinned(Long id) {
        return mutate(id, note -> note.setIsPinned(!Boolean.TRUE.equals(note.getIsPinned())));
    }

    @Override
    public NoteVO toggleArchived(Long id) {
        return mutate(id, note -> note.setIsArchived(!Boolean.TRUE.equals(note.getIsArchived())));
    }

    /**
     * 按 id 取笔记：
     * <ul>
     *   <li>先按 (id, currentUser) 查，命中即 owner，直接返回；</li>
     *   <li>未命中时，若当前用户是实例管理员，按 id 越权取一条，取不到返回 404；</li>
     *   <li>非 owner 非 admin 一律 403，避免通过状态码探测他人资源是否存在。</li>
     * </ul>
     * 知识库内操作不经过本方法，继续走成员表语义。
     */
    private Note loadOwned(Long id) {
        Long currentUserId = UserContext.require();
        Note note = noteMapper.selectOne(Wrappers.<Note>lambdaQuery()
            .eq(Note::getId, id)
            .eq(Note::getUserId, currentUserId));
        if (note != null) {
            return note;
        }
        if (ownershipService.isAdmin(currentUserId)) {
            Note any = noteMapper.selectById(id);
            if (any == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            return any;
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    private NoteVO mutate(Long id, Consumer<Note> mutation) {
        Note note = loadOwned(id);
        mutation.accept(note);
        // 置空时间戳，让数据库的 ON UPDATE CURRENT_TIMESTAMP 生效而不是写回旧值
        note.setCreatedAt(null);
        note.setUpdatedAt(null);
        noteMapper.updateById(note);
        return toVO(noteMapper.selectById(id));
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    private NoteSummaryVO toSummary(Note note) {
        return new NoteSummaryVO(note.getId(), note.getTitle(), note.getIsPinned(), note.getIsArchived(),
            note.getIndexStatus(), note.getIndexError(), note.getUpdatedAt());
    }

    private NoteVO toVO(Note note) {
        return new NoteVO(note.getId(), note.getTitle(), note.getContent(), note.getIsPinned(),
            note.getIsArchived(), note.getContentVersion(), note.getIndexStatus(), note.getIndexError(),
            note.getCreatedAt(), note.getUpdatedAt());
    }
}
