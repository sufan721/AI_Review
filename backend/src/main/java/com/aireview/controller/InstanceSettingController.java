package com.aireview.controller;

import com.aireview.common.Result;
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

    /** 简单请求体。 */
    public record InstanceSettingRequest(String value, String description) {
    }
}
