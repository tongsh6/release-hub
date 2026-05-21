package io.releasehub.interfaces.api.branchrule;

import io.releasehub.application.branchrule.BranchRuleTestResult;
import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleScope;
import io.releasehub.domain.branchrule.BranchRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BranchRule API Controller
 */
@RestController
@RequestMapping("/api/v1/branch-rules")
@RequiredArgsConstructor
@Tag(name = "BranchRule", description = "分支规则管理 API")
public class BranchRuleController {

    private final BranchRuleUseCase branchRuleUseCase;

    @GetMapping
    @Operation(summary = "获取所有分支规则")
    public ApiResponse<List<BranchRuleView>> list() {
        List<BranchRule> rules = branchRuleUseCase.list();
        List<BranchRuleView> views = rules.stream()
                                          .map(this::toView)
                                          .toList();
        return ApiResponse.success(views);
    }

    @GetMapping("/paged")
    @Operation(summary = "获取分支规则分页列表")
    public ApiPageResponse<List<BranchRuleView>> listPaged(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "name", required = false) String name) {
        var result = branchRuleUseCase.listPaged(name, page, size);
        List<BranchRuleView> views = result.items().stream()
                .map(this::toView)
                .toList();
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取分支规则详情")
    public ApiResponse<BranchRuleView> get(@PathVariable String id) {
        BranchRule rule = branchRuleUseCase.get(id);
        return ApiResponse.success(toView(rule));
    }

    @PostMapping
    @Operation(summary = "创建分支规则")
    public ApiResponse<BranchRuleView> create(@RequestBody CreateBranchRuleRequest request) {
        BranchRuleType type = parseType(request.type());
        BranchRuleScope scope = parseScope(request.scopeLevel(), request.scopeProjectId(), request.scopeSubProjectId());
        BranchRule rule = branchRuleUseCase.create(request.name(), request.pattern(), type,
                request.description(), scope);
        return ApiResponse.success(toView(rule));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分支规则")
    public ApiResponse<BranchRuleView> update(@PathVariable String id, @RequestBody UpdateBranchRuleRequest request) {
        BranchRuleType type = parseType(request.type());
        BranchRuleScope scope = parseScope(request.scopeLevel(), request.scopeProjectId(), request.scopeSubProjectId());
        BranchRule rule = branchRuleUseCase.update(id, request.name(), request.pattern(), type,
                request.description(), scope);
        return ApiResponse.success(toView(rule));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分支规则")
    public ApiResponse<Void> delete(@PathVariable String id) {
        branchRuleUseCase.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用分支规则")
    public ApiResponse<Void> enable(@PathVariable String id) {
        branchRuleUseCase.enable(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用分支规则")
    public ApiResponse<Void> disable(@PathVariable String id) {
        branchRuleUseCase.disable(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/check")
    @Operation(summary = "检查分支名称是否符合规则")
    public ApiResponse<BranchCheckResult> check(@RequestParam String branchName,
                                                @RequestParam(name = "scopeProjectId", required = false) String scopeProjectId,
                                                @RequestParam(name = "scopeSubProjectId", required = false) String scopeSubProjectId) {
        boolean compliant = branchRuleUseCase.isCompliant(branchName, scopeProjectId, scopeSubProjectId);
        return ApiResponse.success(new BranchCheckResult(branchName, compliant));
    }

    @PostMapping("/test")
    @Operation(summary = "测试分支规则匹配")
    public ApiResponse<TestResultView> test(@RequestBody TestBranchRuleRequest request) {
        BranchRuleType type = parseType(request.type());
        BranchRuleTestResult result = branchRuleUseCase.test(request.pattern(), type, request.branchName());
        return ApiResponse.success(new TestResultView(result.ok(), null, result.errors()));
    }

    private BranchRuleView toView(BranchRule rule) {
        return new BranchRuleView(
                rule.getId().value(),
                rule.getName(),
                rule.getPattern(),
                rule.getType().name(),
                rule.getDescription(),
                new ScopeView(
                        rule.getScope().getLevel().name(),
                        rule.getScope().getProjectId(),
                        rule.getScope().getSubProjectId()
                ),
                rule.isEnabled() ? "ENABLED" : "DISABLED",
                rule.getCreatedAt().toString(),
                rule.getUpdatedAt().toString()
        );
    }

    private static BranchRuleType parseType(String type) {
        if (type == null || type.isBlank()) return BranchRuleType.TEMPLATE;
        return BranchRuleType.valueOf(type.toUpperCase());
    }

    private static BranchRuleScope parseScope(String level, String projectId, String subProjectId) {
        if (level == null || level.isBlank()) return BranchRuleScope.global();
        try {
            BranchRuleScope.ScopeLevel scopeLevel = BranchRuleScope.ScopeLevel.valueOf(level.toUpperCase());
            return switch (scopeLevel) {
                case GLOBAL -> BranchRuleScope.global();
                case PROJECT -> BranchRuleScope.project(projectId);
                case SUB_PROJECT -> BranchRuleScope.subProject(projectId, subProjectId);
            };
        } catch (IllegalArgumentException e) {
            return BranchRuleScope.global();
        }
    }

    // --- Views & Requests ---

    public record BranchRuleView(
            String id,
            String name,
            String pattern,
            String type,
            String description,
            ScopeView scope,
            String status,
            String createdAt,
            String updatedAt
    ) {}

    public record ScopeView(
            String level,
            String projectId,
            String subProjectId
    ) {}

    public record CreateBranchRuleRequest(
            String name,
            String pattern,
            String type,
            String description,
            String scopeLevel,
            String scopeProjectId,
            String scopeSubProjectId
    ) {}

    public record UpdateBranchRuleRequest(
            String name,
            String pattern,
            String type,
            String description,
            String scopeLevel,
            String scopeProjectId,
            String scopeSubProjectId
    ) {}

    public record BranchCheckResult(
            String branchName,
            boolean compliant
    ) {}

    public record TestBranchRuleRequest(
            String pattern,
            String type,
            String branchName
    ) {}

    public record TestResultView(
            boolean ok,
            String rendered,
            List<String> errors
    ) {
        public TestResultView {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public List<String> errors() {
            return List.copyOf(errors);
        }
    }
}
