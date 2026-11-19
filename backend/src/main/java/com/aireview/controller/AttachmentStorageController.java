package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.AttachmentStorageSetting;
import com.aireview.dto.AttachmentStorageStatusVO;
import com.aireview.security.RequireRole;
import com.aireview.service.AttachmentService;
import com.aireview.service.InstanceSettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台「附件存储」面板：读取/保存存储方式、查看用量。全部仅管理员可用。
 *
 * <p>切换方式只影响之后的新附件；已存附件按各自记录里的 storage_mode 读取。
 */
@RestController
@RequestMapping("/api/admin/attachment-storage")
public class AttachmentStorageController {

    private final InstanceSettingService instanceSettingService;
    private final AttachmentService attachmentService;

    public AttachmentStorageController(InstanceSettingService instanceSettingService,
        AttachmentService attachmentService) {
        this.instanceSettingService = instanceSettingService;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/setting")
    @RequireRole("ROLE_ADMIN")
    public Result<AttachmentStorageSetting> setting() {
        return Result.ok(instanceSettingService.getAttachmentStorage());
    }

    @PutMapping("/setting")
    @RequireRole("ROLE_ADMIN")
    public Result<AttachmentStorageSetting> updateSetting(
        @Valid @RequestBody AttachmentStorageSetting request) {
        return Result.ok(instanceSettingService.upsertAttachmentStorage(request));
    }

    @GetMapping("/status")
    @RequireRole("ROLE_ADMIN")
    public Result<AttachmentStorageStatusVO> status() {
        return Result.ok(attachmentService.status());
    }
}
