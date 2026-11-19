package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.AttachmentVO;
import com.aireview.entity.Attachment;
import com.aireview.service.AttachmentService;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件上传/下载/删除。登录用户可管理自己的附件，管理员可越权；
 * 字节落点由后台存储方式决定，调用方无需感知。
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<AttachmentVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(attachmentService.upload(file));
    }

    @GetMapping
    public Result<List<AttachmentVO>> list() {
        return Result.ok(attachmentService.list());
    }

    /** 下载是字节流，不套 Result 信封；固定 attachment  disposition 防浏览器内联渲染。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Attachment attachment = attachmentService.loadOwned(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(attachmentService.downloadName(attachment)).build().toString())
            .body(attachmentService.downloadResource(attachment));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        attachmentService.delete(id);
        return Result.ok();
    }
}
