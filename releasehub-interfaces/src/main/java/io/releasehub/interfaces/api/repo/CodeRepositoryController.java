package io.releasehub.interfaces.api.repo;

import io.releasehub.application.repo.CodeRepositoryAppService;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.repo.CodeRepository;
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
        var repo = appService.create(request.getProjectId(), request.getGitlabProjectId(), request.getName(), request.getCloneUrl(), request.getDefaultBranch(), request.isMonoRepo());
        return ApiResponse.success(CodeRepositoryView.fromDomain(repo));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update repository")
    public ApiResponse<CodeRepositoryView> update(@PathVariable("id") String id, @RequestBody @Valid UpdateRepoRequest request) {
        var repo = appService.update(id, request.getGitlabProjectId(), request.getName(), request.getCloneUrl(), request.getDefaultBranch(), request.isMonoRepo());
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

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sync repository statistics from GitLab")
    public ApiResponse<Boolean> sync(@PathVariable("id") String id) {
        appService.syncRepository(id);
        return ApiResponse.success(true);
    }

    @GetMapping
    @Operation(summary = "List repositories")
    public ApiResponse<List<CodeRepositoryView>> list(@RequestParam(name = "keyword", required = false) String keyword,
                                                      @RequestParam(name = "projectId", required = false) String projectId,
                                                      @RequestParam(name = "gitlabProjectId", required = false) Long gitlabProjectId) {
        return ApiResponse.success(appService.search(keyword, projectId, gitlabProjectId).stream().map(CodeRepositoryView::fromDomain).collect(Collectors.toList()));
    }

    @GetMapping("/paged")
    @Operation(summary = "List repositories (paged)")
    public ApiPageResponse<List<CodeRepositoryView>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                               @RequestParam(name = "size", defaultValue = "20") int size,
                                                               @RequestParam(name = "keyword", required = false) String keyword,
                                                               @RequestParam(name = "projectId", required = false) String projectId,
                                                               @RequestParam(name = "gitlabProjectId", required = false) Long gitlabProjectId) {
        List<CodeRepository> all = appService.search(keyword, projectId, gitlabProjectId);
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<CodeRepository> slice = from >= all.size() ? List.of() : all.subList(from, to);
        return ApiPageResponse.success(slice.stream().map(CodeRepositoryView::fromDomain).collect(Collectors.toList()), new PageMeta(page, size, all.size()));
    }
}
