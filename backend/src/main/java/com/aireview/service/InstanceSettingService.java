package com.aireview.service;

import com.aireview.dto.AccessSetting;
import com.aireview.dto.GeneralSetting;
import com.aireview.dto.InstanceSettingVO;
import java.util.List;

/**
 * 实例设置服务（对齐 memos 的 InstanceService 设置部分）。
 *
 * <p>键值存储，value 为 JSON 字符串。除通用 {@link #get(String)} / {@link #upsert} 之外，
 * 另提供 {@link GeneralSetting} / {@link AccessSetting} 的类型安全读取与写入，
 * 避免业务代码到处手拼 JSON。
 */
public interface InstanceSettingService {

    List<InstanceSettingVO> list();

    InstanceSettingVO get(String name);

    InstanceSettingVO upsert(String name, String value, String description);

    /** 读取 GENERAL 设置；缺失时返回 {@link GeneralSetting#defaults()}。 */
    GeneralSetting getGeneral();

    /** 写入 GENERAL 设置；返回最新值。仅管理员可调用（实现层加 {@code @RequireRole}）。 */
    GeneralSetting upsertGeneral(GeneralSetting setting);

    /** 读取 ACCESS 设置；缺失时返回 {@link AccessSetting#defaults()}。 */
    AccessSetting getAccess();

    /** 写入 ACCESS 设置；返回最新值。仅管理员可调用。 */
    AccessSetting upsertAccess(AccessSetting setting);
}
