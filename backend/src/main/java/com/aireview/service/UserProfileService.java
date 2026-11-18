package com.aireview.service;

import com.aireview.dto.UpdateProfileRequest;
import com.aireview.dto.UpdateUserSettingRequest;
import com.aireview.dto.UserProfileVO;
import com.aireview.dto.UserSettingVO;
import java.util.List;

/**
 * 用户资料与设置服务。
 * 覆盖：当前用户资料（display_name/description/avatar_url）、用户设置（GENERAL/TAGS/EDITOR）。
 */
public interface UserProfileService {

    UserProfileVO currentProfile();

    UserProfileVO updateProfile(UpdateProfileRequest request);

    List<UserSettingVO> listSettings();

    UserSettingVO getSetting(String key);

    UserSettingVO updateSetting(UpdateUserSettingRequest request);
}
