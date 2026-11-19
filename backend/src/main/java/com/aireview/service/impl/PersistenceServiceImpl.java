package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.PageResult;
import com.aireview.common.ResultCode;
import com.aireview.dto.BackupRecordVO;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.PersistenceSetting;
import com.aireview.dto.PersistenceStatusVO;
import com.aireview.dto.PersistenceTaskVO;
import com.aireview.dto.RestoreResultVO;
import com.aireview.persistence.PersistenceTaskRegistry;
import com.aireview.security.RequireRole;
import com.aireview.service.BackupService;
import com.aireview.service.InstanceSettingService;
import com.aireview.service.PersistenceService;
import com.aireview.service.RestoreService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** 面板门面：把设置、备份、恢复、任务进度聚合成一组管理员接口。 */
@Service
public class PersistenceServiceImpl implements PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceServiceImpl.class);

    private final InstanceSettingService settingService;
    private final BackupService backupService;
    private final RestoreService restoreService;
    private final PersistenceTaskRegistry tasks;
    private final TaskExecutor backupExecutor;
    private volatile RestoreResultVO lastRestore;

    public PersistenceServiceImpl(InstanceSettingService settingService, BackupService backupService,
        RestoreService restoreService, PersistenceTaskRegistry tasks, @Qualifier("backupTaskExecutor") TaskExecutor backupExecutor) {
        this.settingService = settingService;
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.tasks = tasks;
        this.backupExecutor = backupExecutor;
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public PersistenceSetting getSetting() {
        return settingService.getPersistence();
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public PersistenceSetting updateSetting(PersistenceSetting setting) {
        return settingService.upsertPersistence(setting);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public PersistenceStatusVO status() {
        return backupService.status();
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public synchronized Long startBackup() {
        if (tasks.hasRunning()) {
            throw new BusinessException(ResultCode.CONFLICT, "已有备份/恢复任务在执行，请稍后再试");
        }
        return backupService.start("MANUAL");
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public synchronized Long startRestore(long backupId) {
        if (tasks.hasRunning()) {
            throw new BusinessException(ResultCode.CONFLICT, "已有备份/恢复任务在执行，请稍后再试");
        }
        BackupVerifyVO verify = backupService.verify(backupId);
        if (!verify.valid()) {
            throw new BusinessException(ResultCode.CONFLICT, "归档校验失败：" + verify.reason());
        }
        long taskId = tasks.create("RESTORE", 1);
        try {
            backupExecutor.execute(() -> {
                try {
                    RestoreResultVO result = restoreService.restore(backupId);
                    lastRestore = result;
                    tasks.update(taskId, "SUCCESS", 1,
                        "恢复 " + result.restoredRows() + " 行，安排 " + result.reindexScheduled() + " 项索引重建");
                } catch (RuntimeException exception) {
                    log.error("恢复失败：{}", exception.getMessage(), exception);
                    tasks.update(taskId, "FAILED", 0, "恢复失败：" + exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            tasks.update(taskId, "FAILED", 0, "恢复队列已满");
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "恢复队列已满，请稍后重试");
        }
        return taskId;
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public PageResult<BackupRecordVO> backups(long page, long size) {
        return backupService.list(page, size);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public BackupRecordVO backup(long id) {
        return backupService.get(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public BackupVerifyVO verify(long id) {
        return backupService.verify(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public void deleteBackup(long id) {
        backupService.delete(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public Path archivePath(long id) {
        return backupService.resolveArchive(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public String archiveName(long id) {
        return backupService.fileName(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public PersistenceTaskVO task(long id) {
        return tasks.get(id);
    }

    @Override
    @RequireRole("ROLE_ADMIN")
    public RestoreResultVO lastRestore() {
        return lastRestore == null ? new RestoreResultVO(0, 0) : lastRestore;
    }
}
