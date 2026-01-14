package io.releasehub.interfaces.api.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.common.exception.BizException;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{id}")
    @Operation(summary = "获取版本策略详情")
    public ApiResponse<VersionPolicyView> get(@PathVariable("id") String id) {
        VersionPolicy policy = versionPolicyPort.findById(VersionPolicyId.of(id))
                .orElseThrow(() -> new BizException("POLICY_NOT_FOUND", "VersionPolicy not found: " + id));
        return ApiResponse.success(VersionPolicyView.fromDomain(policy));
    }
}
