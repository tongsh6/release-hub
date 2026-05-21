package io.releasehub.interfaces.api.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import io.releasehub.domain.version.VersionPolicyScope;
import io.releasehub.domain.version.VersionScheme;
import io.releasehub.domain.version.BumpRule;
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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 版本策略 API 控制器
 */
@RestController
@RequestMapping("/api/v1/version-policies")
@RequiredArgsConstructor
@Tag(name = "版本策略 - VersionPolicy")
public class VersionPolicyController {

    private final VersionPolicyPort versionPolicyPort;

    @GetMapping
    @Operation(summary = "获取所有版本策略")
    public ApiResponse<List<VersionPolicyView>> list() {
        List<VersionPolicyView> policies = versionPolicyPort.findAll()
                .stream()
                .map(VersionPolicyView::fromDomain)
                .collect(Collectors.toList());
        return ApiResponse.success(policies);
    }

    @GetMapping("/paged")
    @Operation(summary = "获取版本策略分页列表")
    public ApiPageResponse<List<VersionPolicyView>> listPaged(@RequestParam(name = "page", defaultValue = "1") int page,
                                                              @RequestParam(name = "size", defaultValue = "20") int size,
                                                              @RequestParam(name = "keyword", required = false) String keyword) {
        var result = versionPolicyPort.findPaged(keyword, page, size);
        List<VersionPolicyView> views = result.items().stream()
                .map(VersionPolicyView::fromDomain)
                .collect(Collectors.toList());
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @GetMapping("/applicable")
    @Operation(summary = "获取指定作用域可继承的版本策略")
    public ApiResponse<List<VersionPolicyView>> applicable(@RequestParam(name = "scopeProjectId", required = false) String scopeProjectId,
                                                           @RequestParam(name = "scopeSubProjectId", required = false) String scopeSubProjectId) {
        List<VersionPolicyView> views = versionPolicyPort.findAll().stream()
                .filter(policy -> policy.getScope().matches(scopeProjectId, scopeSubProjectId))
                .sorted(Comparator
                        .comparingInt((VersionPolicy policy) -> policy.getScope().specificity()).reversed()
                        .thenComparing(VersionPolicy::getUpdatedAt, Comparator.reverseOrder())
                        .thenComparing(policy -> policy.getId().value()))
                .map(VersionPolicyView::fromDomain)
                .toList();
        return ApiResponse.success(views);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取版本策略详情")
    public ApiResponse<VersionPolicyView> get(@PathVariable("id") String id) {
        VersionPolicy policy = versionPolicyPort.findById(VersionPolicyId.of(id))
                .orElseThrow(() -> NotFoundException.versionPolicy(id));
        return ApiResponse.success(VersionPolicyView.fromDomain(policy));
    }

    @PostMapping
    @Operation(summary = "创建版本策略")
    public ApiResponse<VersionPolicyView> create(@RequestBody CreateVersionPolicyRequest request) {
        VersionPolicy policy = VersionPolicy.create(
                request.name(),
                parseScheme(request.scheme()),
                parseBumpRule(request.bumpRule()),
                parseScope(request.scopeLevel(), request.scopeProjectId(), request.scopeSubProjectId()),
                Instant.now());
        versionPolicyPort.save(policy);
        return ApiResponse.success(VersionPolicyView.fromDomain(policy));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新版本策略")
    public ApiResponse<VersionPolicyView> update(@PathVariable("id") String id, @RequestBody UpdateVersionPolicyRequest request) {
        VersionPolicy existing = versionPolicyPort.findById(VersionPolicyId.of(id))
                .orElseThrow(() -> NotFoundException.versionPolicy(id));
        VersionPolicy updated = existing.update(
                request.name(),
                parseScheme(request.scheme()),
                parseBumpRule(request.bumpRule()),
                parseScope(request.scopeLevel(), request.scopeProjectId(), request.scopeSubProjectId()),
                Instant.now());
        versionPolicyPort.save(updated);
        return ApiResponse.success(VersionPolicyView.fromDomain(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除版本策略")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        versionPolicyPort.findById(VersionPolicyId.of(id))
                .orElseThrow(() -> NotFoundException.versionPolicy(id));
        versionPolicyPort.deleteById(VersionPolicyId.of(id));
        return ApiResponse.success(null);
    }

    private static VersionScheme parseScheme(String scheme) {
        if (scheme == null || scheme.isBlank()) {
            return VersionScheme.SEMVER;
        }
        return VersionScheme.valueOf(scheme.toUpperCase());
    }

    private static BumpRule parseBumpRule(String bumpRule) {
        if (bumpRule == null || bumpRule.isBlank()) {
            return BumpRule.PATCH;
        }
        return BumpRule.valueOf(bumpRule.toUpperCase());
    }

    private static VersionPolicyScope parseScope(String level, String projectId, String subProjectId) {
        if (level == null || level.isBlank()) {
            return VersionPolicyScope.global();
        }
        try {
            VersionPolicyScope.ScopeLevel scopeLevel = VersionPolicyScope.ScopeLevel.valueOf(level.toUpperCase());
            return switch (scopeLevel) {
                case GLOBAL -> VersionPolicyScope.global();
                case PROJECT -> VersionPolicyScope.project(projectId);
                case SUB_PROJECT -> VersionPolicyScope.subProject(projectId, subProjectId);
            };
        } catch (IllegalArgumentException e) {
            return VersionPolicyScope.global();
        }
    }

    public record CreateVersionPolicyRequest(
            String name,
            String scheme,
            String bumpRule,
            String scopeLevel,
            String scopeProjectId,
            String scopeSubProjectId
    ) {}

    public record UpdateVersionPolicyRequest(
            String name,
            String scheme,
            String bumpRule,
            String scopeLevel,
            String scopeProjectId,
            String scopeSubProjectId
    ) {}
}
