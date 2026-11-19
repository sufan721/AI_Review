package com.aireview.service;

import com.aireview.common.PageResult;
import com.aireview.dto.BackupRecordVO;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.PersistenceStatusVO;
import com.aireview.entity.BackupRecord;
import java.nio.file.Path;

/** 数据快照的创建、查询、校验、下载与清理。 */
public interface BackupService {

    /** 异步触发一次备份，返回 backup_record.id。并发已有任务时抛 409。 */
    Long start(String trigger);

    /** 恢复前的同步安全快照（keep_forever=1）；失败则恢复流程直接中止。 */
    BackupRecord snapshotForRestore();

    /** 后台线程实际执行；taskId<=0 时不更新进度。 */
    void run(long recordId, long taskId, String lockToken);

    BackupRecordVO get(long id);

    PageResult<BackupRecordVO> list(long page, long size);

    PersistenceStatusVO status();

    BackupVerifyVO verify(long id);

    /** 校验白名单后返回归档绝对路径，供下载/恢复使用。 */
    Path resolveArchive(long id);

    String fileName(long id);

    void delete(long id);

    /** 按保留天数清理过期归档，只删本应用生成且未标记 keep_forever 的文件。 */
    void cleanExpired();
}
