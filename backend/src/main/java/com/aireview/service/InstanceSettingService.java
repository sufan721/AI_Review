package com.aireview.service;

import com.aireview.dto.InstanceSettingVO;
import java.util.List;

/**
 * 实例设置服务（对齐 memos 的 InstanceService 设置部分）。
 */
public interface InstanceSettingService {

    List<InstanceSettingVO> list();

    InstanceSettingVO get(String name);

    InstanceSettingVO upsert(String name, String value, String description);
}
