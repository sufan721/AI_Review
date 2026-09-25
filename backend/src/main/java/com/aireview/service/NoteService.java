package com.aireview.service;

import com.aireview.common.PageResult;
import com.aireview.dto.NoteCreateRequest;
import com.aireview.dto.NoteQueryRequest;
import com.aireview.dto.NoteSummaryVO;
import com.aireview.dto.NoteUpdateRequest;
import com.aireview.dto.NoteVO;

public interface NoteService {
    PageResult<NoteSummaryVO> list(NoteQueryRequest query);

    NoteVO create(NoteCreateRequest request);

    NoteVO detail(Long id);

    NoteVO update(Long id, NoteUpdateRequest request);

    void delete(Long id);

    NoteVO reindex(Long id);

    NoteVO togglePinned(Long id);

    NoteVO toggleArchived(Long id);
}
