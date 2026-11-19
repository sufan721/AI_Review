package com.aireview.controller;

import com.aireview.common.PageResult;
import com.aireview.common.Result;
import com.aireview.dto.BackupRecordVO;
import com.aireview.dto.BackupVerifyVO;
import com.aireview.dto.PersistenceSetting;
import com.aireview.dto.PersistenceStatusVO;
import com.aireview.dto.PersistenceTaskVO;
import com.aireview.dto.RestoreResultVO;
import com.aireview.security.RequireRole;
import com.aireview.service.PersistenceService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台「数据持久化」面板：设置、状态、备份历史、下载、校验与恢复。 */
@RestController
@RequestMapping("/api/admin/persistence")
public class PersistenceController {

    private final PersistenceService persistenceService;

    public PersistenceController(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping("/setting")
    @RequireRole("ROLE_ADMIN")
    public Result<PersistenceSetting> setting() {
        return Result.ok(persistenceService.getSetting());
    }

    @PutMapping("/setting")
    @RequireRole("ROLE_ADMIN")
    public Result<PersistenceSetting> updateSetting(@Valid @RequestBody PersistenceSetting request) {
        return Result.ok(persistenceService.updateSetting(request));
    }

    @GetMapping("/status")
    @RequireRole("ROLE_ADMIN")
    public Result<PersistenceStatusVO> status() {
        return Result.ok(persistenceService.status());
    }

    @PostMapping("/backups")
    @RequireRole("ROLE_ADMIN")
    public ResponseEntity<Result<BackupStartVO>> startBackup() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Result.ok(new BackupStartVO(persistenceService.startBackup())));
    }

    @GetMapping("/backups")
    @RequireRole("ROLE_ADMIN")
    public Result<PageResult<BackupRecordVO>> backups(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size) {
        return Result.ok(persistenceService.backups(page, size));
    }

    @GetMapping("/backups/{id}")
    @RequireRole("ROLE_ADMIN")
    public Result<BackupRecordVO> backup(@PathVariable long id) {
        return Result.ok(persistenceService.backup(id));
    }

    @PostMapping("/backups/{id}/verify")
    @RequireRole("ROLE_ADMIN")
    public Result<BackupVerifyVO> verify(@PathVariable long id) {
        return Result.ok(persistenceService.verify(id));
    }

    @DeleteMapping("/backups/{id}")
    @RequireRole("ROLE_ADMIN")
    public Result<Void> deleteBackup(@PathVariable long id) {
        persistenceService.deleteBackup(id);
        return Result.ok();
    }

    /** 下载是字节流，不套 Result 信封；前端需用原生 fetch 带 token 解析 blob。 */
    @GetMapping("/backups/{id}/download")
    @RequireRole("ROLE_ADMIN")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Path file = persistenceService.archivePath(id);
        String name = persistenceService.archiveName(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(name).build().toString())
            .body(new FileSystemResource(file));
    }

    @PostMapping("/backups/{id}/restore")
    @RequireRole("ROLE_ADMIN")
    public ResponseEntity<Result<RestoreStartVO>> startRestore(@PathVariable long id,
        @RequestBody(required = false) RestoreConfirmRequest request) {
        if (request == null || !"RESTORE".equals(request.confirm())) {
            return ResponseEntity.badRequest()
                .body(Result.<RestoreStartVO>fail(400, "请输入 RESTORE 确认恢复"));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Result.ok(new RestoreStartVO(persistenceService.startRestore(id))));
    }

    @GetMapping("/tasks/{id}")
    @RequireRole("ROLE_ADMIN")
    public Result<PersistenceTaskVO> task(@PathVariable long id) {
        return Result.ok(persistenceService.task(id));
    }

    @GetMapping("/last-restore")
    @RequireRole("ROLE_ADMIN")
    public Result<RestoreResultVO> lastRestore() {
        return Result.ok(persistenceService.lastRestore());
    }

    public record BackupStartVO(long id) {
    }

    public record RestoreStartVO(long taskId) {
    }

    public record RestoreConfirmRequest(String confirm) {
    }
}
