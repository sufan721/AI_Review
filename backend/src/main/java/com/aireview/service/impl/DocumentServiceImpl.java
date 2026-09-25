package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.PageResult;
import com.aireview.common.ResultCode;
import com.aireview.dto.DocumentSummaryVO;
import com.aireview.dto.DocumentVO;
import com.aireview.entity.Document;
import com.aireview.index.IndexingService;
import com.aireview.index.ResourceType;
import com.aireview.mapper.DocumentMapper;
import com.aireview.service.DocumentService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentServiceImpl implements DocumentService {
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final String INDEX_STATUS_PENDING = "PENDING";

    private final DocumentMapper documentMapper;
    private final IndexingService indexingService;

    public DocumentServiceImpl(DocumentMapper documentMapper, IndexingService indexingService) {
        this.documentMapper = documentMapper;
        this.indexingService = indexingService;
    }

    @Override
    public PageResult<DocumentSummaryVO> list(long page, long size) {
        Page<Document> result = documentMapper.selectPage(new Page<>(page, size),
            Wrappers.<Document>lambdaQuery()
                .eq(Document::getUserId, UserContext.require())
                .orderByDesc(Document::getUpdatedAt));
        return PageResult.of(result.getRecords().stream().map(this::toSummary).toList(),
            result.getTotal(), page, size);
    }

    @Override
    public DocumentVO upload(MultipartFile file) {
        validate(file);
        Document document = new Document();
        document.setUserId(UserContext.require());
        document.setFileName(file.getOriginalFilename().trim());
        document.setFileSize(file.getSize());
        document.setContentVersion(1);
        document.setIndexStatus(INDEX_STATUS_PENDING);
        try {
            document.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取 Markdown 文件");
        }
        documentMapper.insert(document);
        indexingService.schedule(ResourceType.DOCUMENT, document.getId());
        return toVO(documentMapper.selectById(document.getId()));
    }

    @Override
    public DocumentVO detail(Long id) {
        return toVO(loadOwned(id));
    }

    @Override
    public void delete(Long id) {
        documentMapper.deleteById(loadOwned(id).getId());
        indexingService.deleteResource(ResourceType.DOCUMENT, id);
    }

    @Override
    public DocumentVO reindex(Long id) {
        loadOwned(id);
        indexingService.reindex(ResourceType.DOCUMENT, id);
        return toVO(documentMapper.selectById(id));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择非空 Markdown 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 .md 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Markdown 文件不能超过 2MB");
        }
    }

    private Document loadOwned(Long id) {
        Document document = documentMapper.selectOne(Wrappers.<Document>lambdaQuery()
            .eq(Document::getId, id)
            .eq(Document::getUserId, UserContext.require()));
        if (document == null) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return document;
    }

    private DocumentSummaryVO toSummary(Document document) {
        return new DocumentSummaryVO(document.getId(), document.getFileName(), document.getFileSize(),
            document.getContentVersion(), document.getIndexStatus(), document.getIndexError(),
            document.getCreatedAt(), document.getUpdatedAt());
    }

    private DocumentVO toVO(Document document) {
        return new DocumentVO(document.getId(), document.getFileName(), document.getContent(), document.getFileSize(),
            document.getContentVersion(), document.getIndexStatus(), document.getIndexError(),
            document.getCreatedAt(), document.getUpdatedAt());
    }
}
