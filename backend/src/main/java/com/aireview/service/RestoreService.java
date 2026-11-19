package com.aireview.service;

import com.aireview.dto.RestoreResultVO;

/** 从已校验的归档恢复到当前实例。 */
public interface RestoreService {

    RestoreResultVO restore(long backupId);
}
