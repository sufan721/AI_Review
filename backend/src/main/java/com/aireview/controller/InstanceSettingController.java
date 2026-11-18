package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.AccessSetting;
import com.aireview.dto.GeneralSetting;
import com.aireview.dto.InstanceSettingVO;
import com.aireview.service.InstanceSettingService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实例设置接口（对齐 memos 的 InstanceService 设置部分）。
 *
 * <p>除通用 KV 接口外，提供 GENERAL / ACCESS 两个类型安全的便捷接口，
 * 供管理后台直接读写布尔开关与访问模式，避免前端手拼 JSON。
 */
@RestController
@RequestMapping("/api/instance/settings")
public class InstanceSettingController {

    private final InstanceSettingService instanceSettingService;

    public InstanceSettingController(InstanceSettingService instanceSettingService) {
        this.instanceSettingService = instanceSettingService;
    }

    @GetMapping
    public Result<List<InstanceSettingVO>> list() {
        return Result.ok(instanceSettingService.list());
    }

    @GetMapping("/{name}")
    public Result<InstanceSettingVO> get(@PathVariable String name) {
        return Result.ok(instanceSettingService.get(name));
    }

    @PutMapping("/{name}")
    public Result<InstanceSettingVO> upsert(@PathVariable @NotBlank String name,
        @RequestBody(required = false) InstanceSettingRequest request) {
        String value = request == null ? null : request.value();
        String description = request == null ? null : request.description();
        return Result.ok(instanceSettingService.upsert(name, value, description));
    }

    // ---- 类型安全便捷接口 ----

    /** 读取注册/密码登录开关；匿名可读，用于登录页判断是否展示注册入口。 */
    @GetMapping("/general")
    public Result<GeneralSetting> general() {
        return Result.ok(instanceSettingService.getGeneral());
    }

    /** 更新注册/密码登录开关；仅管理员。 */
    @PutMapping("/general")
    public Result<GeneralSetting> updateGeneral(@RequestBody GeneralSettingRequest request) {
        GeneralSetting setting = new GeneralSetting(
            request.disallowUserRegistration(),
            request.disallowPasswordAuth(),
            request.forbidChangeUsername(),
            request.forbidChangeNickname());
        return Result.ok(instanceSettingService.upsertGeneral(setting));
    }

    /** 读取访问模式；匿名可读，用于前端判断是否允许匿名浏览公开资源。 */
    @GetMapping("/access")
    public Result<AccessSetting> access() {
        return Result.ok(instanceSettingService.getAccess());
    }

    /** 更新访问模式；仅管理员。 */
    @PutMapping("/access")
    public Result<AccessSetting> updateAccess(@RequestBody AccessModeRequest request) {
        AccessSetting.Mode mode = AccessSetting.Mode.valueOf(
            request.mode().toUpperCase(java.util.Locale.ROOT));
        return Result.ok(instanceSettingService.upsertAccess(new AccessSetting(mode)));
    }

    /** 简单请求体。 */
    public record InstanceSettingRequest(String value, String description) {
    }

    public record GeneralSettingRequest(boolean disallowUserRegistration,
        boolean disallowPasswordAuth,
        boolean forbidChangeUsername,
        boolean forbidChangeNickname) {
    }

    public record AccessModeRequest(String mode) {
    }
}
