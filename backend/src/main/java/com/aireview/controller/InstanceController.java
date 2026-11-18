package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实例级公开状态接口。
 *
 * <p>仅暴露匿名可读的实例元信息，不涉及敏感配置。
 * {@link #bootstrap()} 供注册页判断是否首次注册（空库时首位注册者自动成为管理员）。
 */
@RestController
@RequestMapping("/api/instance")
public class InstanceController {

    private final UserMapper userMapper;

    public InstanceController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 实例是否已初始化（存在任意用户）。匿名可读，由 PublicAccessRegistry 放行。 */
    @GetMapping("/bootstrap")
    public Result<BootstrapStatus> bootstrap() {
        boolean initialized = userMapper.selectCount(null) > 0;
        return Result.ok(new BootstrapStatus(initialized));
    }

    /** 实例初始化状态。 */
    public record BootstrapStatus(boolean initialized) {
    }
}
