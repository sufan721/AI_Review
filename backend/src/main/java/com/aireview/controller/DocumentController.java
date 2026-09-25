package com.aireview.controller;

import com.aireview.common.PageResult;
import com.aireview.common.Result;
import com.aireview.dto.DocumentSummaryVO;
import com.aireview.dto.DocumentVO;
import com.aireview.service.DocumentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public Result<PageResult<DocumentSummaryVO>> list(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return Result.ok(documentService.list(page, size));
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<DocumentVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(documentService.upload(file));
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reindex")
    public Result<DocumentVO> reindex(@PathVariable Long id) {
        return Result.ok(documentService.reindex(id));
    }
}
