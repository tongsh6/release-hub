package io.releasehub.interfaces.api.iteration;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationView;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
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

import java.util.List;
import java.util.Set;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/iterations")
@RequiredArgsConstructor
public class IterationController {
    private final IterationAppService iterationAppService;

    @PostMapping
    public ApiResponse<String> create(@RequestBody CreateIterationRequest request) {
        var it = iterationAppService.create(request.getIterationKey(), request.getDescription(), request.getRepoIds());
        return ApiResponse.success(it.getId().value());
    }

    @GetMapping("/{key}")
    public ApiResponse<IterationView> get(@PathVariable("key") String key) {
        var it = iterationAppService.get(key);
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @GetMapping
    public ApiResponse<List<IterationView>> list() {
        var list = iterationAppService.list().stream().map(IterationView::fromDomain).toList();
        return ApiResponse.success(list);
    }

    @GetMapping("/_paged")
    public ApiPageResponse<List<IterationView>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                          @RequestParam(name = "size", defaultValue = "20") int size) {
        var all = iterationAppService.list().stream().map(IterationView::fromDomain).toList();
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<IterationView> slice = from >= all.size() ? List.<IterationView>of() : all.subList(from, to);
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }

    @PutMapping("/{key}")
    public ApiResponse<IterationView> update(@PathVariable("key") String key, @RequestBody UpdateIterationRequest request) {
        var it = iterationAppService.update(key, request.getDescription(), request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @PostMapping("/{key}/repos/add")
    public ApiResponse<IterationView> addRepos(@PathVariable("key") String key, @RequestBody RepoChangeRequest request) {
        var it = iterationAppService.addRepos(key, request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @PostMapping("/{key}/repos/remove")
    public ApiResponse<IterationView> removeRepos(@PathVariable("key") String key, @RequestBody RepoChangeRequest request) {
        var it = iterationAppService.removeRepos(key, request.getRepoIds());
        return ApiResponse.success(IterationView.fromDomain(it));
    }

    @GetMapping("/{key}/repos")
    public ApiResponse<java.util.Set<String>> listRepos(@PathVariable("key") String key) {
        var repos = iterationAppService.listRepos(key);
        return ApiResponse.success(repos);
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> delete(@PathVariable("key") String key) {
        iterationAppService.delete(key);
        return ApiResponse.success(null);
    }

    @Data
    public static class CreateIterationRequest {
        private String iterationKey;
        private String description;
        private Set<String> repoIds;
    }

    @Data
    public static class UpdateIterationRequest {
        private String description;
        private Set<String> repoIds;
    }

    @Data
    public static class RepoChangeRequest {
        private Set<String> repoIds;
    }
}
