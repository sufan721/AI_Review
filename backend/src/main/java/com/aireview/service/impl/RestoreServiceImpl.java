package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.RestoreResultVO;
import com.aireview.entity.Document;
import com.aireview.entity.Note;
import com.aireview.index.IndexingService;
import com.aireview.index.ResourceType;
import com.aireview.mapper.DocumentMapper;
import com.aireview.mapper.NoteMapper;
import com.aireview.persistence.RestoreWriter;
import com.aireview.service.BackupService;
import com.aireview.service.RestoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** 恢复编排：校验 → 恢复前快照 → 事务覆盖 → 标记索引待重建 → 后台重建向量。 */
@Service
public class RestoreServiceImpl implements RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreServiceImpl.class);

    private final BackupService backupService;
    private final RestoreWriter restoreWriter;
    private final NoteMapper noteMapper;
    private final DocumentMapper documentMapper;
    private final IndexingService indexingService;
    private final TaskExecutor backupExecutor;

    public RestoreServiceImpl(BackupService backupService, RestoreWriter restoreWriter,
        NoteMapper noteMapper, DocumentMapper documentMapper, IndexingService indexingService,
        @Qualifier("backupTaskExecutor") TaskExecutor backupExecutor) {
        this.backupService = backupService;
        this.restoreWriter = restoreWriter;
        this.noteMapper = noteMapper;
        this.documentMapper = documentMapper;
        this.indexingService = indexingService;
        this.backupExecutor = backupExecutor;
    }

    @Override
    public RestoreResultVO restore(long backupId) {
        BackupVerifyVO verify = backupService.verify(backupId);
        if (!verify.valid()) {
            throw new BusinessException(ResultCode.CONFLICT, "归档校验失败：" + verify.reason());
        }
        // 拿不到恢复前快照就不允许继续：这是唯一的安全回退点
        backupService.snapshotForRestore();

        Path archive = backupService.resolveArchive(backupId);
        RestoreResultVO result = restoreWriter.write(archive);

        // 归档里的 index_status 可能来自另一台机器/另一个 Milvus，一律重算
        Note pendingNote = new Note();
        pendingNote.setIndexStatus("PENDING");
        noteMapper.update(pendingNote, new LambdaQueryWrapper<Note>());
        Document pendingDocument = new Document();
        pendingDocument.setIndexStatus("PENDING");
        documentMapper.update(pendingDocument, new LambdaQueryWrapper<Document>());

        List<Note> notes = noteMapper.selectList(null);
        List<Document> documents = documentMapper.selectList(null);
        for (Note note : notes) {
            schedule(() -> indexingService.indexNow(ResourceType.NOTE, note.getId(), note.getUserId()));
        }
        for (Document document : documents) {
            schedule(() -> indexingService.indexNow(ResourceType.DOCUMENT, document.getId(), document.getUserId()));
        }
        log.info("恢复完成：{} 行，已安排 {} 个资料重建索引", result.restoredRows(), result.reindexScheduled());
        return result;
    }

    private void schedule(Runnable job) {
        try {
            backupExecutor.execute(() -> {
                try {
                    job.run();
                } catch (RuntimeException exception) {
                    log.warn("恢复后重建索引失败：{}", exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            log.warn("重建索引任务队列已满：{}", exception.getMessage());
        }
    }
}
