package io.releasehub.interfaces.api.iteration;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.iteration.IterationView;
import io.releasehub.application.iteration.VersionConflict;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.ConflictResolution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/iterations")
@RequiredArgsConstructor
@Tag(name = "迭代管理 - 迭代设置")
public class IterationController {
    private final IterationAppService iterationAppService;

    @PostMapping
    @Operation(summary = "Create iteration")
    public ApiResponse<IterationView> create(@RequestBody CreateIterationRequest request) {
        var it = iterationAppService.create(request.getName(), request.getDescription(), request.getExpectedReleaseAt(), request.getGroupCode(), request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get iteration")
    public ApiResponse<IterationView> get(@PathVariable("key") String key) {
        var it = iterationAppService.get(key);
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @GetMapping
    @Operation(summary = "List iterations")
    public ApiResponse<List<IterationView>> list() {
        var list = iterationAppService.list().stream().map(IterationView::fromDomain).toList();
        return ApiResponse.success(list);
    }

    @GetMapping("/paged")
    @Operation(summary = "List iterations (paged)")
    public ApiPageResponse<List<IterationView>> listPaged(@RequestParam(name = "page", defaultValue = "1") int page,
                                                          @RequestParam(name = "size", defaultValue = "20") int size,
                                                          @RequestParam(name = "keyword", required = false) String keyword) {
        var result = iterationAppService.listPaged(keyword, page, size);
        List<IterationView> views = result.items().stream().map(IterationView::fromDomain).toList();
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update iteration")
    public ApiResponse<IterationView> update(@PathVariable("key") String key, @RequestBody UpdateIterationRequest request) {
        var it = iterationAppService.update(key, request.getName(), request.getDescription(), request.getExpectedReleaseAt(), request.getGroupCode(), request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @PostMapping("/{key}/repos/add")
    @Operation(summary = "Add repos to iteration")
    public ApiResponse<IterationView> addRepos(@PathVariable("key") String key, @RequestBody RepoChangeRequest request) {
        var it = iterationAppService.addRepos(key, request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @PostMapping("/{key}/repos/remove")
    @Operation(summary = "Remove repos from iteration")
    public ApiResponse<IterationView> removeRepos(@PathVariable("key") String key, @RequestBody RepoChangeRequest request) {
        var it = iterationAppService.removeRepos(key, request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @GetMapping("/{key}/repos")
    @Operation(summary = "List repos of iteration")
    public ApiResponse<java.util.Set<String>> listRepos(@PathVariable("key") String key) {
        var repos = iterationAppService.listRepos(key);
        return ApiResponse.success(repos);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete iteration")
    public ApiResponse<Void> delete(@PathVariable("key") String key) {
        iterationAppService.delete(key);
        return ApiResponse.success(null);
    }

    // ==== 版本管理 API ====

    @GetMapping("/{key}/repos/{repoId}/version-info")
    @Operation(summary = "Get repo version info for iteration")
    public ApiResponse<IterationRepoVersionInfo> getRepoVersionInfo(
            @PathVariable("key") String key,
            @PathVariable("repoId") String repoId) {
        var versionInfo = iterationAppService.getIterationRepoVersionInfo(
                IterationKey.of(key), RepoId.of(repoId));
        return ApiResponse.success(versionInfo);
    }

    @GetMapping("/{key}/repos/{repoId}/check-conflict")
    @Operation(summary = "Check version conflict for repo in iteration")
    public ApiResponse<VersionConflict> checkVersionConflict(
            @PathVariable("key") String key,
            @PathVariable("repoId") String repoId) {
        var conflict = iterationAppService.checkVersionConflict(key, repoId);
        return ApiResponse.success(conflict);
    }

    @PostMapping("/{key}/repos/{repoId}/sync-version")
    @Operation(summary = "Sync version from repository")
    public ApiResponse<IterationRepoVersionInfo> syncVersionFromRepo(
            @PathVariable("key") String key,
            @PathVariable("repoId") String repoId) {
        var versionInfo = iterationAppService.syncVersionFromRepo(
                IterationKey.of(key), RepoId.of(repoId));
        return ApiResponse.success(versionInfo);
    }

    @PostMapping("/{key}/repos/{repoId}/resolve-conflict")
    @Operation(summary = "Resolve version conflict")
    public ApiResponse<IterationRepoVersionInfo> resolveVersionConflict(
            @PathVariable("key") String key,
            @PathVariable("repoId") String repoId,
            @RequestBody ResolveConflictRequest request) {
        var versionInfo = iterationAppService.resolveVersionConflict(
                IterationKey.of(key), RepoId.of(repoId), request.getResolution());
        return ApiResponse.success(versionInfo);
    }

    @Data
    public static class CreateIterationRequest {
        @NotBlank
        private String name;
        private String description;
        private LocalDate expectedReleaseAt;
        @NotBlank
        private String groupCode;
        private Set<String> repoIds;
    }

    @Data
    public static class UpdateIterationRequest {
        private String name;
        private String description;
        private LocalDate expectedReleaseAt;
        @NotBlank
        private String groupCode;
        private Set<String> repoIds;
    }

    @Data
    public static class RepoChangeRequest {
        private Set<String> repoIds;
    }

    @Data
    public static class ResolveConflictRequest {
        private ConflictResolution resolution;
    }
}
