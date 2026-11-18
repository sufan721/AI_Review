package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.IdentityProviderRequest;
import com.aireview.dto.IdentityProviderVO;
import com.aireview.service.IdentityProviderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份源接口（对齐 memos 的 IdentityProviderService）。
 */
@RestController
@RequestMapping("/api/identity-providers")
public class IdentityProviderController {

    private final IdentityProviderService identityProviderService;

    public IdentityProviderController(IdentityProviderService identityProviderService) {
        this.identityProviderService = identityProviderService;
    }

    @GetMapping
    public Result<List<IdentityProviderVO>> list() {
        return Result.ok(identityProviderService.list());
    }

    @GetMapping("/{uid}")
    public Result<IdentityProviderVO> get(@PathVariable String uid) {
        return Result.ok(identityProviderService.get(uid));
    }

    @PostMapping
    public Result<IdentityProviderVO> create(@Valid @RequestBody IdentityProviderRequest request) {
        return Result.ok(identityProviderService.create(request));
    }

    @PatchMapping("/{uid}")
    public Result<IdentityProviderVO> update(@PathVariable String uid,
        @Valid @RequestBody IdentityProviderRequest request) {
        return Result.ok(identityProviderService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public Result<Void> delete(@PathVariable String uid) {
        identityProviderService.delete(uid);
        return Result.ok();
    }
}
