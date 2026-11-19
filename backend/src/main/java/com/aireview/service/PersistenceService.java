package com.aireview.service;

import com.aireview.common.PageResult;
import com.aireview.dto.BackupRecordVO;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.PersistenceSetting;
import com.aireview.dto.PersistenceStatusVO;
import com.aireview.dto.PersistenceTaskVO;
import com.aireview.dto.RestoreResultVO;
import java.nio.file.Path;

/** 管理后台「数据持久化」面板的统一入口。 */
public interface PersistenceService {

    PersistenceSetting getSetting();

    PersistenceSetting updateSetting(PersistenceSetting setting);

    PersistenceStatusVO status();

    Long startBackup();

    Long startRestore(long backupId);

    PageResult<BackupRecordVO> backups(long page, long size);

    BackupRecordVO backup(long id);

    BackupVerifyVO verify(long id);

    void deleteBackup(long id);

    Path archivePath(long id);

    String archiveName(long id);

    PersistenceTaskVO task(long id);

    RestoreResultVO lastRestore();
}
