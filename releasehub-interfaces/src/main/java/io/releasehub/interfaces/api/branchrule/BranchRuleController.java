package io.releasehub.interfaces.api.branchrule;

import io.releasehub.application.branchrule.BranchRuleAppService;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BranchRule API Controller
 */
@RestController
@RequestMapping("/api/v1/branch-rules")
@RequiredArgsConstructor
@Tag(name = "BranchRule", description = "分支规则管理 API")
public class BranchRuleController {

    private final BranchRuleAppService branchRuleAppService;

    @GetMapping
    @Operation(summary = "获取所有分支规则")
    public ApiResponse<List<BranchRuleView>> list() {
        List<BranchRule> rules = branchRuleAppService.list();
        List<BranchRuleView> views = rules.stream()
                                          .map(this::toView)
                                          .toList();
        return ApiResponse.success(views);
    }

    @GetMapping("/paged")
    @Operation(summary = "获取分支规则分页列表")
    public ApiPageResponse<List<BranchRuleView>> listPaged(@RequestParam(name = "page", defaultValue = "1") int page,
                                                           @RequestParam(name = "size", defaultValue = "20") int size,
                                                           @RequestParam(name = "name", required = false) String name) {
        var result = branchRuleAppService.listPaged(name, page, size);
        List<BranchRuleView> views = result.items().stream()
                .map(this::toView)
                .toList();
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    private BranchRuleView toView(BranchRule rule) {
        return new BranchRuleView(
                rule.getId().value(),
                rule.getName(),
                rule.getPattern(),
                rule.getType().name(),
                rule.getCreatedAt().toString(),
                rule.getUpdatedAt().toString()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取分支规则详情")
    public ApiResponse<BranchRuleView> get(@PathVariable String id) {
        BranchRule rule = branchRuleAppService.get(id);
        return ApiResponse.success(toView(rule));
    }

    @PostMapping
    @Operation(summary = "创建分支规则")
    public ApiResponse<BranchRuleView> create(@RequestBody CreateBranchRuleRequest request) {
        BranchRuleType type = BranchRuleType.valueOf(request.type().toUpperCase());
        BranchRule rule = branchRuleAppService.create(request.name(), request.pattern(), type);
        return ApiResponse.success(toView(rule));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分支规则")
    public ApiResponse<BranchRuleView> update(@PathVariable String id, @RequestBody UpdateBranchRuleRequest request) {
        BranchRuleType type = BranchRuleType.valueOf(request.type().toUpperCase());
        BranchRule rule = branchRuleAppService.update(id, request.name(), request.pattern(), type);
        return ApiResponse.success(toView(rule));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分支规则")
    public ApiResponse<Void> delete(@PathVariable String id) {
        branchRuleAppService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/check")
    @Operation(summary = "检查分支名称是否符合规则")
    public ApiResponse<BranchCheckResult> check(@RequestParam String branchName) {
        boolean compliant = branchRuleAppService.isCompliant(branchName);
        return ApiResponse.success(new BranchCheckResult(branchName, compliant));
    }

    public record BranchRuleView(
            String id,
            String name,
            String pattern,
            String type,
            String createdAt,
            String updatedAt
    ) {
    }

    public record CreateBranchRuleRequest(
            String name,
            String pattern,
            String type
    ) {
    }

    public record UpdateBranchRuleRequest(
            String name,
            String pattern,
            String type
    ) {
    }

    public record BranchCheckResult(
            String branchName,
            boolean compliant
    ) {
    }
}
