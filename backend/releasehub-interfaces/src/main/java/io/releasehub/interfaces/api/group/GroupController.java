package io.releasehub.interfaces.api.group;

import io.releasehub.application.group.GroupAppService;
import io.releasehub.application.group.GroupNodeView;
import io.releasehub.application.group.GroupView;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.util.stream.Collectors;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "分组管理 - 分组设置")
public class GroupController {
    private final GroupAppService groupAppService;

    @PostMapping
    @Operation(summary = "Create group")
    public ApiResponse<String> create(@Valid @RequestBody CreateGroupRequest request) {
        var g = groupAppService.create(request.getName(), request.getCode(), request.getParentCode());
        return ApiResponse.success(g.getId().value());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group by id")
    public ApiResponse<GroupView> get(@PathVariable("id") String id) {
        var g = groupAppService.get(id);
        return ApiResponse.success(GroupView.fromDomain(g));
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Get group by code")
    public ApiResponse<GroupView> getByCode(@PathVariable("code") String code) {
        var g = groupAppService.getByCode(code);
        return ApiResponse.success(GroupView.fromDomain(g));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update group")
    public ApiResponse<GroupView> update(@PathVariable("id") String id, @Valid @RequestBody UpdateGroupRequest request) {
        var g = groupAppService.update(id, request.getName(), request.getParentCode());
        return ApiResponse.success(GroupView.fromDomain(g));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group by id")
    public ApiResponse<Void> deleteById(@PathVariable("id") String id) {
        groupAppService.deleteById(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/by-code/{code}")
    @Operation(summary = "Delete group by code")
    public ApiResponse<Void> deleteByCode(@PathVariable("code") String code) {
        groupAppService.deleteByCode(code);
        return ApiResponse.success(null);
    }

    @GetMapping
    @Operation(summary = "List groups")
    public ApiResponse<List<GroupView>> list() {
        var list = groupAppService.list().stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/paged")
    @Operation(summary = "List groups (paged)")
    public ApiPageResponse<List<GroupView>> listPaged(@RequestParam(name = "page", defaultValue = "1") int page,
                                                      @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = groupAppService.listPaged(page, size);
        List<GroupView> views = result.items().stream().map(GroupView::fromDomain).collect(Collectors.toList());
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @GetMapping("/children/{parentCode}")
    @Operation(summary = "List children by parent code")
    public ApiResponse<List<GroupView>> children(@PathVariable("parentCode") String parentCode) {
        var list = groupAppService.children(parentCode).stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/top-level")
    @Operation(summary = "List top-level groups")
    public ApiResponse<List<GroupView>> topLevel() {
        var list = groupAppService.topLevel().stream()
                                  .map(GroupView::fromDomain)
                                  .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/tree")
    @Operation(summary = "Get group tree")
    public ApiResponse<List<GroupNodeView>> tree() {
        var tree = groupAppService.tree();
        return ApiResponse.success(tree);
    }

    @Data
    public static class CreateGroupRequest {
        @NotBlank
        @Size(max = 128)
        private String name;
        @Size(max = 64)
        private String code;
        @Size(max = 64)
        private String parentCode;
    }

    @Data
    public static class UpdateGroupRequest {
        @NotBlank
        @Size(max = 128)
        private String name;
        @Size(max = 64)
        private String parentCode;
    }
}
