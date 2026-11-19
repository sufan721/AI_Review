package com.aireview.mapper;

import com.aireview.entity.Attachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;

public interface AttachmentMapper extends BaseMapper<Attachment> {

    /** 按 storage_mode 聚合附件数与总字节数（用于管理员概览）。 */
    List<Map<String, Object>> selectUsageByMode();
}
