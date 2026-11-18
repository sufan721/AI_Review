package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.InstanceSettingVO;
import com.aireview.entity.InstanceSetting;
import com.aireview.mapper.InstanceSettingMapper;
import com.aireview.security.RequireRole;
import com.aireview.service.InstanceSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实例设置服务实现。
 * 读取公开（用于 instance profile），写入需管理员。
 */
@Service
public class InstanceSettingServiceImpl implements InstanceSettingService {

    private final InstanceSettingMapper instanceSettingMapper;

    public InstanceSettingServiceImpl(InstanceSettingMapper instanceSettingMapper) {
        this.instanceSettingMapper = instanceSettingMapper;
    }

    @Override
    public List<InstanceSettingVO> list() {
        return instanceSettingMapper.selectList(null).stream()
            .map(s -> new InstanceSettingVO(s.getName(), s.getValue(), s.getDescription()))
            .toList();
    }

    @Override
    public InstanceSettingVO get(String name) {
        InstanceSetting setting = instanceSettingMapper.selectOne(
            new LambdaQueryWrapper<InstanceSetting>()
                .eq(InstanceSetting::getName, normalizeName(name)));
        if (setting == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return new InstanceSettingVO(setting.getName(), setting.getValue(), setting.getDescription());
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public InstanceSettingVO upsert(String name, String value, String description) {
        String key = normalizeName(name);
        InstanceSetting setting = instanceSettingMapper.selectOne(
            new LambdaQueryWrapper<InstanceSetting>().eq(InstanceSetting::getName, key));

        if (setting == null) {
            InstanceSetting created = new InstanceSetting();
            created.setName(key);
            created.setValue(value);
            created.setDescription(description);
            instanceSettingMapper.insert(created);
            return new InstanceSettingVO(key, value, description);
        }

        setting.setValue(value);
        if (description != null) {
            setting.setDescription(description);
        }
        instanceSettingMapper.updateById(setting);
        return new InstanceSettingVO(key, value, setting.getDescription());
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
