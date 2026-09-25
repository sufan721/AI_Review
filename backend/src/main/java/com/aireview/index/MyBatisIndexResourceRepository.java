package com.aireview.index;

import com.aireview.entity.Document;
import com.aireview.entity.Note;
import com.aireview.mapper.DocumentMapper;
import com.aireview.mapper.NoteMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的资料读写。笔记与文件两张表结构一致，按类型分派。
 */
@Repository
public class MyBatisIndexResourceRepository implements IndexResourceRepository {
    static final String INDEXED = "INDEXED";
    static final String FAILED = "FAILED";
    static final String PENDING = "PENDING";

    private final NoteMapper noteMapper;
    private final DocumentMapper documentMapper;

    public MyBatisIndexResourceRepository(NoteMapper noteMapper, DocumentMapper documentMapper) {
        this.noteMapper = noteMapper;
        this.documentMapper = documentMapper;
    }

    @Override
    public IndexResource load(ResourceType type, long resourceId, long userId) {
        if (type == ResourceType.NOTE) {
            Note note = noteMapper.selectOne(Wrappers.<Note>lambdaQuery()
                .eq(Note::getId, resourceId).eq(Note::getUserId, userId));
            return note == null ? null : toResource(note.getId(), note.getUserId(),
                note.getContent(), note.getContentVersion(), note.getIndexStatus());
        }
        Document document = documentMapper.selectOne(Wrappers.<Document>lambdaQuery()
            .eq(Document::getId, resourceId).eq(Document::getUserId, userId));
        return document == null ? null : toResource(document.getId(), document.getUserId(),
            document.getContent(), document.getContentVersion(), document.getIndexStatus());
    }

    @Override
    public void markIndexed(ResourceType type, long resourceId) {
        if (type == ResourceType.NOTE) {
            noteMapper.update(null, Wrappers.<Note>lambdaUpdate().eq(Note::getId, resourceId)
                .set(Note::getIndexStatus, INDEXED)
                .set(Note::getIndexError, null)
                .set(Note::getRetryCount, 0));
            return;
        }
        documentMapper.update(null, Wrappers.<Document>lambdaUpdate().eq(Document::getId, resourceId)
            .set(Document::getIndexStatus, INDEXED)
            .set(Document::getIndexError, null)
            .set(Document::getRetryCount, 0));
    }

    @Override
    public void markFailed(ResourceType type, long resourceId, String reason) {
        if (type == ResourceType.NOTE) {
            noteMapper.update(null, Wrappers.<Note>lambdaUpdate().eq(Note::getId, resourceId)
                .set(Note::getIndexStatus, FAILED)
                .set(Note::getIndexError, reason)
                .setSql("retry_count = retry_count + 1"));
            return;
        }
        documentMapper.update(null, Wrappers.<Document>lambdaUpdate().eq(Document::getId, resourceId)
            .set(Document::getIndexStatus, FAILED)
            .set(Document::getIndexError, reason)
            .setSql("retry_count = retry_count + 1"));
    }

    @Override
    public void markPending(ResourceType type, long resourceId) {
        if (type == ResourceType.NOTE) {
            noteMapper.update(null, Wrappers.<Note>lambdaUpdate().eq(Note::getId, resourceId)
                .set(Note::getIndexStatus, PENDING)
                .set(Note::getIndexError, null));
            return;
        }
        documentMapper.update(null, Wrappers.<Document>lambdaUpdate().eq(Document::getId, resourceId)
            .set(Document::getIndexStatus, PENDING)
            .set(Document::getIndexError, null));
    }

    private IndexResource toResource(long id, long userId, String content, Integer version, String status) {
        int contentVersion = version == null ? 1 : version;
        return new IndexResource(id, userId, content == null ? "" : content, contentVersion, status);
    }
}
