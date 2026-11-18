package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.UpdateProfileRequest;
import com.aireview.dto.UpdateUserSettingRequest;
import com.aireview.dto.UserProfileVO;
import com.aireview.dto.UserSettingVO;
import com.aireview.service.UserProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料与设置接口（memos 语义扩展）。
 *
 * <p>注意：这里以「当前登录用户」为对象，资源路径统一走 /api/users/me，
 * 避免与现有 AuthController 的 /api/auth/me 冲突，同时保留 memos 的 users/{user} 语义可扩展性。
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public Result<UserProfileVO> profile() {
        return Result.ok(userProfileService.currentProfile());
    }

    @PatchMapping
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userProfileService.updateProfile(request));
    }

    @GetMapping("/settings")
    public Result<List<UserSettingVO>> listSettings() {
        return Result.ok(userProfileService.listSettings());
    }

    @GetMapping("/settings/{key}")
    public Result<UserSettingVO> getSetting(@PathVariable String key) {
        return Result.ok(userProfileService.getSetting(key));
    }

    @PatchMapping("/settings")
    public Result<UserSettingVO> updateSetting(@Valid @RequestBody UpdateUserSettingRequest request) {
        return Result.ok(userProfileService.updateSetting(request));
    }
}
