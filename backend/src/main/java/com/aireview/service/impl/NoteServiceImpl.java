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
import com.aireview.mapper.NoteMapper;
import com.aireview.service.NoteService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class NoteServiceImpl implements NoteService {
    /** 正文变更后待重新索引，实际索引任务在索引功能中投递。 */
    private static final String INDEX_STATUS_PENDING = "PENDING";

    private final NoteMapper noteMapper;

    public NoteServiceImpl(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
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
        // 回读以拿到数据库生成的时间戳与默认值
        return toVO(noteMapper.selectById(note.getId()));
    }

    @Override
    public NoteVO detail(Long id) {
        return toVO(loadOwned(id));
    }

    @Override
    public NoteVO update(Long id, NoteUpdateRequest request) {
        return mutate(id, note -> {
            note.setTitle(normalizeTitle(request.title()));
            String content = request.content() == null ? "" : request.content();
            if (!Objects.equals(note.getContent(), content)) {
                note.setContent(content);
                note.setContentVersion(note.getContentVersion() + 1);
                note.setIndexStatus(INDEX_STATUS_PENDING);
            }
        });
    }

    @Override
    public void delete(Long id) {
        Note note = loadOwned(id);
        noteMapper.deleteById(note.getId());
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
     * 按 id 取笔记时一律带上 user_id 条件：查不到既可能是记录不存在，也可能是别人的资源，
     * 统一返回 403 以免通过状态码探测他人资源是否存在。
     */
    private Note loadOwned(Long id) {
        Note note = noteMapper.selectOne(Wrappers.<Note>lambdaQuery()
            .eq(Note::getId, id)
            .eq(Note::getUserId, UserContext.require()));
        if (note == null) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return note;
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
            note.getIndexStatus(), note.getUpdatedAt());
    }

    private NoteVO toVO(Note note) {
        return new NoteVO(note.getId(), note.getTitle(), note.getContent(), note.getIsPinned(),
            note.getIsArchived(), note.getContentVersion(), note.getIndexStatus(), note.getCreatedAt(),
            note.getUpdatedAt());
    }
}
