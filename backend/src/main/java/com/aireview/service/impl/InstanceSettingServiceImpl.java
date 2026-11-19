package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.AccessSetting;
import com.aireview.dto.AttachmentStorageSetting;
import com.aireview.dto.GeneralSetting;
import com.aireview.dto.InstanceSettingVO;
import com.aireview.dto.PersistenceSetting;
import com.aireview.entity.InstanceSetting;
import com.aireview.mapper.InstanceSettingMapper;
import com.aireview.security.RequireRole;
import com.aireview.service.InstanceSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实例设置服务实现。
 * 读取公开（用于 instance profile），写入需管理员。
 */
@Service
public class InstanceSettingServiceImpl implements InstanceSettingService {

    /** 实例设置中各类业务 key（统一大写）。 */
    public static final String NAME_GENERAL = "GENERAL";
    public static final String NAME_ACCESS = "ACCESS";
    public static final String NAME_PERSISTENCE = "PERSISTENCE";
    public static final String NAME_STORAGE = "STORAGE";

    private static final Logger log = LoggerFactory.getLogger(InstanceSettingServiceImpl.class);
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

    @Override
    public GeneralSetting getGeneral() {
        InstanceSetting setting = find(NAME_GENERAL);
        return setting == null ? GeneralSetting.defaults() : GeneralSetting.fromJson(setting.getValue());
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public GeneralSetting upsertGeneral(GeneralSetting setting) {
        String json = setting.toJson();
        upsert(NAME_GENERAL, json, "注册/登录/资料编辑开关");
        return setting;
    }

    @Override
    public AccessSetting getAccess() {
        InstanceSetting setting = find(NAME_ACCESS);
        return setting == null ? AccessSetting.defaults() : AccessSetting.fromJson(setting.getValue());
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public AccessSetting upsertAccess(AccessSetting setting) {
        String json = setting.toJson();
        upsert(NAME_ACCESS, json, "实例访问模式 PUBLIC/PRIVATE");
        return setting;
    }

    @Override
    public PersistenceSetting getPersistence() {
        InstanceSetting setting = find(NAME_PERSISTENCE);
        if (setting == null || setting.getValue() == null || setting.getValue().isBlank()) {
            return PersistenceSetting.defaults();
        }
        if (!PersistenceSetting.isValidJson(setting.getValue())) {
            log.warn("instance_setting.PERSISTENCE 中的 JSON 已损坏，本次读取回退默认值");
        }
        return PersistenceSetting.fromJson(setting.getValue());
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public PersistenceSetting upsertPersistence(PersistenceSetting setting) {
        if (setting == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设置不能为空");
        }
        PersistenceSetting normalized;
        try {
            normalized = setting.normalize();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, exception.getMessage());
        }
        if (normalized.enabled()) {
            assertWritable(Path.of(normalized.storagePath()));
        }
        upsert(NAME_PERSISTENCE, normalized.toJson(), "数据快照备份策略");
        return normalized;
    }

    @Override
    public AttachmentStorageSetting getAttachmentStorage() {
        InstanceSetting setting = find(NAME_STORAGE);
        if (setting == null || setting.getValue() == null || setting.getValue().isBlank()) {
            return AttachmentStorageSetting.defaults();
        }
        if (!AttachmentStorageSetting.isValidJson(setting.getValue())) {
            log.warn("instance_setting.STORAGE 中的 JSON 已损坏，本次读取回退默认值");
        }
        return AttachmentStorageSetting.fromJson(setting.getValue());
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public AttachmentStorageSetting upsertAttachmentStorage(AttachmentStorageSetting setting) {
        if (setting == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设置不能为空");
        }
        AttachmentStorageSetting normalized;
        try {
            normalized = setting.normalize();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, exception.getMessage());
        }
        // 仅文件系统模式需要目录可写；数据库模式不碰文件系统
        if (normalized.mode() == AttachmentStorageSetting.Mode.LOCAL_FS) {
            assertStorageWritable(Path.of(normalized.localDir()));
        }
        upsert(NAME_STORAGE, normalized.toJson(), "附件存储方式 LOCAL_FS/DATABASE");
        return normalized;
    }

    /** 保存附件目录前验证可创建可写，失败信息里带上具体目录。 */
    private void assertStorageWritable(Path directory) {
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, ".aireview-attach-test-", ".tmp");
            Files.deleteIfExists(probe);
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "附件目录不可写：" + directory + "（" + exception.getMessage() + "）");
        }
    }

    /** 保存前验证目录可用，避免「配置成功但备份必然失败」。 */
    private void assertWritable(Path directory) {
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, ".aireview-write-test-", ".tmp");
            Files.deleteIfExists(probe);
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "备份目录不可写：" + directory + "（" + exception.getMessage() + "）");
        }
    }

    private InstanceSetting find(String name) {
        return instanceSettingMapper.selectOne(
            new LambdaQueryWrapper<InstanceSetting>().eq(InstanceSetting::getName, name));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
