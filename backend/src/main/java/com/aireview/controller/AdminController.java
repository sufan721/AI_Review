package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.AdminUserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.RequireRole;
import com.aireview.service.AdminUserService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口：列出用户、授予/撤销 ADMIN、改状态、删号。
 * 全部需要 ROLE_ADMIN；写操作带「最后一个活跃 admin 保护」。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserMapper userMapper;
    private final AdminUserService adminUserService;

    public AdminController(UserMapper userMapper, AdminUserService adminUserService) {
        this.userMapper = userMapper;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    @RequireRole("ROLE_ADMIN")
    public Result<List<AdminUserVO>> users() {
        List<AdminUserVO> users = userMapper.selectList(null).stream()
            .map(AdminController::toVO)
            .toList();
        return Result.ok(users);
    }

    @PostMapping("/users/{id}/roles/admin")
    @RequireRole("ROLE_ADMIN")
    public Result<Void> grantAdmin(@PathVariable Long id) {
        adminUserService.grantAdmin(id);
        return Result.ok(null);
    }

    @DeleteMapping("/users/{id}/roles/admin")
    @RequireRole("ROLE_ADMIN")
    public Result<Void> revokeAdmin(@PathVariable Long id) {
        adminUserService.revokeAdmin(id);
        return Result.ok(null);
    }

    @PutMapping("/users/{id}/status")
    @RequireRole("ROLE_ADMIN")
    public Result<Void> updateStatus(@PathVariable Long id,
        @RequestParam int status) {
        adminUserService.updateStatus(id, status);
        return Result.ok(null);
    }

    @DeleteMapping("/users/{id}")
    @RequireRole("ROLE_ADMIN")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return Result.ok(null);
    }

    private static AdminUserVO toVO(User user) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getStatus());
    }
}
