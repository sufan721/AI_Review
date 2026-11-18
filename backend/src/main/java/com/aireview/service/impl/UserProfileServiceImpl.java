package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.GeneralSetting;
import com.aireview.dto.UpdateProfileRequest;
import com.aireview.dto.UpdateUserSettingRequest;
import com.aireview.dto.UserProfileVO;
import com.aireview.dto.UserSettingVO;
import com.aireview.entity.User;
import com.aireview.entity.UserSetting;
import com.aireview.mapper.UserMapper;
import com.aireview.mapper.UserSettingMapper;
import com.aireview.service.InstanceSettingService;
import com.aireview.service.UserProfileService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 用户资料与设置服务实现。
 * 复用现有 UserMapper 的角色查询；设置走 user_setting 表。
 * 资料编辑受实例开关 forbidChangeUsername / forbidChangeNickname 约束。
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    /** 允许用户自行更新的设置 key（GENERAL/TAGS/EDITOR）。 */
    private static final Set<String> UPDATABLE_KEYS = Set.of("GENERAL", "TAGS", "EDITOR");

    private final UserMapper userMapper;
    private final UserSettingMapper userSettingMapper;
    private final InstanceSettingService instanceSettingService;

    public UserProfileServiceImpl(UserMapper userMapper, UserSettingMapper userSettingMapper,
        InstanceSettingService instanceSettingService) {
        this.userMapper = userMapper;
        this.userSettingMapper = userSettingMapper;
        this.instanceSettingService = instanceSettingService;
    }

    @Override
    public UserProfileVO currentProfile() {
        User user = loadCurrentUser();
        return toProfile(user);
    }

    @Override
    public UserProfileVO updateProfile(UpdateProfileRequest request) {
        Long userId = UserContext.require();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        GeneralSetting general = instanceSettingService.getGeneral();

        // 用户名变更：受 forbidChangeUsername 约束，需查重并踢旧会话
        if (request.username() != null) {
            if (general.forbidChangeUsername()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "管理员已禁止修改用户名");
            }
            String trimmed = request.username().trim();
            if (!trimmed.equals(user.getUsername())) {
                Long dup = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getUsername, trimmed)
                    .ne(User::getId, userId));
                if (dup != null && dup > 0) {
                    throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "用户名已被占用");
                }
                User rename = new User();
                rename.setId(userId);
                rename.setUsername(trimmed);
                userMapper.updateById(rename);
                // 用户名变更后踢旧会话，强制重新登录
                userMapper.bumpTokenVersion(userId);
                user.setUsername(trimmed);
            }
        }

        // 昵称 / 描述 / 头像：昵称受 forbidChangeNickname 约束
        User update = new User();
        update.setId(userId);
        boolean dirty = false;
        if (request.displayName() != null) {
            if (general.forbidChangeNickname()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "管理员已禁止修改昵称");
            }
            update.setNickname(request.displayName().trim());
            user.setNickname(request.displayName().trim());
            dirty = true;
        }
        if (request.description() != null) {
            update.setDescription(request.description().trim());
            user.setDescription(request.description().trim());
            dirty = true;
        }
        if (request.avatarUrl() != null) {
            update.setAvatarUrl(request.avatarUrl().trim());
            user.setAvatarUrl(request.avatarUrl().trim());
            dirty = true;
        }
        if (dirty) {
            userMapper.updateById(update);
        }

        return toProfile(user);
    }

    @Override
    public List<UserSettingVO> listSettings() {
        Long userId = UserContext.require();
        List<UserSetting> settings = userSettingMapper.selectList(
            new LambdaQueryWrapper<UserSetting>().eq(UserSetting::getUserId, userId));
        return settings.stream()
            .map(s -> new UserSettingVO(s.getKey(), s.getValue()))
            .toList();
    }

    @Override
    public UserSettingVO getSetting(String key) {
        Long userId = UserContext.require();
        UserSetting setting = userSettingMapper.selectOne(
            new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .eq(UserSetting::getKey, normalizeKey(key)));
        if (setting == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return new UserSettingVO(setting.getKey(), setting.getValue());
    }

    @Override
    public UserSettingVO updateSetting(UpdateUserSettingRequest request) {
        String key = normalizeKey(request.key());
        if (!UPDATABLE_KEYS.contains(key)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持更新 GENERAL/TAGS/EDITOR 设置");
        }
        Long userId = UserContext.require();

        UserSetting existing = userSettingMapper.selectOne(
            new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .eq(UserSetting::getKey, key));

        if (existing == null) {
            UserSetting setting = new UserSetting();
            setting.setUserId(userId);
            setting.setKey(key);
            setting.setValue(request.value());
            userSettingMapper.insert(setting);
            return new UserSettingVO(key, request.value());
        }

        existing.setValue(request.value());
        userSettingMapper.updateById(existing);
        return new UserSettingVO(key, request.value());
    }

    private User loadCurrentUser() {
        Long userId = UserContext.require();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return user;
    }

    private UserProfileVO toProfile(User user) {
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        return new UserProfileVO(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getDescription(),
            user.getAvatarUrl(),
            roles,
            user.getCreatedAt() == null ? null : user.getCreatedAt().toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli());
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }
}
