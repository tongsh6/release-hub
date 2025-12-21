package io.releasehub.interfaces.api.group;

import io.releasehub.application.group.GroupAppService;
import io.releasehub.application.group.GroupNodeView;
import io.releasehub.application.group.GroupView;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupAppService groupAppService;

    @PostMapping
    public ApiResponse<String> create(@RequestBody CreateGroupRequest request) {
        var g = groupAppService.create(request.getName(), request.getCode(), request.getParentCode());
        return ApiResponse.success(g.getId().value());
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupView> get(@PathVariable("id") String id) {
        var g = groupAppService.get(id);
        return ApiResponse.success(GroupView.fromDomain(g));
    }

    @GetMapping("/by-code/{code}")
    public ApiResponse<GroupView> getByCode(@PathVariable("code") String code) {
        var g = groupAppService.getByCode(code);
        return ApiResponse.success(GroupView.fromDomain(g));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteById(@PathVariable("id") String id) {
        groupAppService.deleteById(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/by-code/{code}")
    public ApiResponse<Void> deleteByCode(@PathVariable("code") String code) {
        groupAppService.deleteByCode(code);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<GroupView>> list() {
        var list = groupAppService.list().stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/_paged")
    public ApiPageResponse<List<GroupView>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                      @RequestParam(name = "size", defaultValue = "20") int size) {
        var all = groupAppService.list().stream().map(GroupView::fromDomain).collect(Collectors.toList());
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<GroupView> slice = from >= all.size() ? java.util.List.<GroupView>of() : all.subList(from, to);
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }

    @GetMapping("/children/{parentCode}")
    public ApiResponse<List<GroupView>> children(@PathVariable("parentCode") String parentCode) {
        var list = groupAppService.children(parentCode).stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/top-level")
    public ApiResponse<List<GroupView>> topLevel() {
        var list = groupAppService.topLevel().stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/tree")
    public ApiResponse<List<GroupNodeView>> tree() {
        var tree = groupAppService.tree();
        return ApiResponse.success(tree);
    }

    @Data
    public static class CreateGroupRequest {
        private String name;
        private String code;
        private String parentCode;
    }
}
