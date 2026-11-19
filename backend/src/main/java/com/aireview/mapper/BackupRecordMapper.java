package com.aireview.mapper;

import com.aireview.entity.BackupRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;

public interface BackupRecordMapper extends BaseMapper<BackupRecord> {

    /** 单飞锁的兜底：查询是否已有任务在跑（跨线程/跨请求可见）。 */
    BackupRecord selectRunning();

    /** 超出保留期且未标记 keep_forever 的成功归档。 */
    List<BackupRecord> selectExpired(LocalDateTime before);
}
