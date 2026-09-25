package com.aireview.service;

import com.aireview.common.PageResult;
import com.aireview.dto.DocumentSummaryVO;
import com.aireview.dto.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    PageResult<DocumentSummaryVO> list(long page, long size);

    DocumentVO upload(MultipartFile file);

    DocumentVO detail(Long id);

    void delete(Long id);

    DocumentVO reindex(Long id);
}
