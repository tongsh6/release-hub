package io.releasehub.interfaces.api.repo;

import io.releasehub.application.repo.CodeRepositoryAppService;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
@Tag(name = "代码仓库 - 仓库管理")
public class CodeRepositoryController {
    private final CodeRepositoryAppService appService;

    @PostMapping
    @Operation(summary = "Create repository")
    public ApiResponse<CodeRepositoryView> create(@RequestBody @Valid CreateRepoRequest request) {
        var repo = appService.create(
                request.getName(),
                request.getCloneUrl(),
                request.getDefaultBranch(),
                request.isMonoRepo(),
                request.getInitialVersion(),
                request.getGroupCode()
        );
        return ApiResponse.success(CodeRepositoryView.fromDomain(repo));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update repository")
    public ApiResponse<CodeRepositoryView> update(@PathVariable("id") String id, @RequestBody @Valid UpdateRepoRequest request) {
        var repo = appService.update(
                id,
                request.getName(),
                request.getCloneUrl(),
                request.getDefaultBranch(),
                request.isMonoRepo(),
                request.getInitialVersion(),
                request.getGroupCode()
        );
        return ApiResponse.success(CodeRepositoryView.fromDomain(repo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get repository")
    public ApiResponse<CodeRepositoryView> get(@PathVariable("id") String id) {
        return ApiResponse.success(CodeRepositoryView.fromDomain(appService.get(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete repository")
    public ApiResponse<Boolean> delete(@PathVariable("id") String id) {
        appService.delete(id);
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/gate-summary")
    @Operation(summary = "Get repository gate summary")
    public ApiResponse<RepoGateSummaryView> getGateSummary(@PathVariable("id") String id) {
        var summary = appService.getGateSummary(id);
        return ApiResponse.success(new RepoGateSummaryView(
                summary.protectedBranch(),
                summary.approvalRequired(),
                summary.pipelineGate(),
                summary.permissionDenied()
        ));
    }

    @GetMapping("/{id}/branch-summary")
    @Operation(summary = "Get repository branch/MR summary")
    public ApiResponse<RepoBranchSummaryView> getBranchSummary(@PathVariable("id") String id) {
        var summary = appService.getBranchSummary(id);
        return ApiResponse.success(new RepoBranchSummaryView(
                summary.totalBranches(),
                summary.activeBranches(),
                summary.nonCompliantBranches(),
                summary.activeMrs(),
                summary.mergedMrs(),
                summary.closedMrs()
        ));
    }

    @GetMapping
    @Operation(summary = "List repositories")
    public ApiResponse<List<CodeRepositoryView>> list(@RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.success(appService.search(keyword).stream().map(CodeRepositoryView::fromDomain).collect(Collectors.toList()));
    }

    @GetMapping("/paged")
    @Operation(summary = "List repositories (paged)")
    public ApiPageResponse<List<CodeRepositoryView>> listPaged(@RequestParam(name = "page", defaultValue = "1") int page,
                                                               @RequestParam(name = "size", defaultValue = "20") int size,
                                                               @RequestParam(name = "keyword", required = false) String keyword) {
        var result = appService.searchPaged(keyword, page, size);
        List<CodeRepositoryView> views = result.items().stream()
                .map(CodeRepositoryView::fromDomain)
                .collect(Collectors.toList());
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @GetMapping("/{id}/initial-version")
    @Operation(summary = "Get repository initial version")
    public ApiResponse<InitialVersionView> getInitialVersion(@PathVariable("id") String id) {
        String version = appService.getInitialVersion(id);
        return ApiResponse.success(new InitialVersionView(id, version));
    }

    @PutMapping("/{id}/initial-version")
    @Operation(summary = "Set repository initial version manually")
    public ApiResponse<InitialVersionView> setInitialVersion(@PathVariable("id") String id, @RequestBody @Valid SetInitialVersionRequest request) {
        appService.setInitialVersion(id, request.getVersion());
        return ApiResponse.success(new InitialVersionView(id, request.getVersion()));
    }

    @PostMapping("/{id}/sync-version")
    @Operation(summary = "Sync initial version from repository")
    public ApiResponse<InitialVersionView> syncInitialVersion(@PathVariable("id") String id) {
        String version = appService.syncInitialVersionFromRepo(id);
        return ApiResponse.success(new InitialVersionView(id, version));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sync repository statistics from GitLab")
    public ApiResponse<CodeRepositoryView> sync(@PathVariable("id") String id) {
        var repo = appService.sync(id);
        return ApiResponse.success(CodeRepositoryView.fromDomain(repo));
    }
}
