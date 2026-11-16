package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.AdminUserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.RequireRole;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅管理员可访问，用于演示 RBAC 角色校验。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserMapper userMapper;

    public AdminController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/users")
    @RequireRole("ROLE_ADMIN")
    public Result<List<AdminUserVO>> users() {
        List<AdminUserVO> users = userMapper.selectList(null).stream()
            .map(AdminController::toVO)
            .toList();
        return Result.ok(users);
    }

    private static AdminUserVO toVO(User user) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getStatus());
    }
}
