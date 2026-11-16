package com.aireview.controller;

import com.aireview.common.Result;
import com.aireview.dto.KnowledgeBaseCreateRequest;
import com.aireview.dto.KnowledgeBaseMemberVO;
import com.aireview.dto.KnowledgeBaseResourceVO;
import com.aireview.dto.KnowledgeBaseUpdateRequest;
import com.aireview.dto.KnowledgeBaseVO;
import com.aireview.dto.MemberAddRequest;
import com.aireview.dto.MemberRoleUpdateRequest;
import com.aireview.dto.ResourceAddRequest;
import com.aireview.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return Result.ok(knowledgeBaseService.create(request));
    }

    @GetMapping
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.ok(knowledgeBaseService.list());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return Result.ok(knowledgeBaseService.detail(id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id,
        @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return Result.ok(knowledgeBaseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}/members")
    public Result<List<KnowledgeBaseMemberVO>> members(@PathVariable Long id) {
        return Result.ok(knowledgeBaseService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @Valid @RequestBody MemberAddRequest request) {
        knowledgeBaseService.addMember(id, request);
        return Result.ok(null);
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long id, @PathVariable Long userId,
        @Valid @RequestBody MemberRoleUpdateRequest request) {
        knowledgeBaseService.updateMemberRole(id, userId, request);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        knowledgeBaseService.removeMember(id, userId);
        return Result.ok(null);
    }

    @GetMapping("/{id}/resources")
    public Result<List<KnowledgeBaseResourceVO>> resources(@PathVariable Long id) {
        return Result.ok(knowledgeBaseService.listResources(id));
    }

    @PostMapping("/{id}/resources")
    public Result<Void> addResource(@PathVariable Long id, @Valid @RequestBody ResourceAddRequest request) {
        knowledgeBaseService.addResource(id, request);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}/resources/{resourceType}/{resourceId}")
    public Result<Void> removeResource(@PathVariable Long id, @PathVariable String resourceType,
        @PathVariable Long resourceId) {
        knowledgeBaseService.removeResource(id, resourceType, resourceId);
        return Result.ok(null);
    }
}
