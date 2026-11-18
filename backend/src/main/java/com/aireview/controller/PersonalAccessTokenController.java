package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.PatCreatedVO;
import com.aireview.dto.PatVO;
import com.aireview.service.PersonalAccessTokenService;
import com.aireview.util.UserContext;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人访问令牌接口（对齐 memos PersonalAccessTokenService）。
 *
 * <p>当前登录用户管理自己的 PAT：创建（返回原始令牌一次）、列表、吊销。
 * PAT 命中后权限等同该用户，可用于脚本/CI 调用受保护接口。
 */
@RestController
@RequestMapping("/api/users/me/pats")
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService patService;

    public PersonalAccessTokenController(PersonalAccessTokenService patService) {
        this.patService = patService;
    }

    @PostMapping
    public Result<PatCreatedVO> issue(@Valid @RequestBody PatCreateRequest request) {
        Duration ttl = request.ttlDays() == null ? null : Duration.ofDays(request.ttlDays());
        return Result.ok(patService.issue(UserContext.require(), request.description(), ttl));
    }

    @GetMapping
    public Result<List<PatVO>> list() {
        return Result.ok(patService.list(UserContext.require()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        patService.revoke(UserContext.require(), id);
        return Result.ok(null);
    }

    /** 创建 PAT 请求体；ttlDays 为空表示不过期。 */
    public record PatCreateRequest(String description, Integer ttlDays) {
    }
}
