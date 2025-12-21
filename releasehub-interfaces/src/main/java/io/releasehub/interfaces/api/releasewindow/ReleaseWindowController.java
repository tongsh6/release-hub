package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.releasewindow.ReleaseWindowAppService;
import io.releasehub.application.releasewindow.ReleaseWindowView;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 发布窗口设置")
public class ReleaseWindowController {

    private final ReleaseWindowAppService appService;

    @PostMapping
    @Operation(summary = "Create release window")
    public ApiResponse<ReleaseWindowView> create(@Valid @RequestBody CreateReleaseWindowRequest request) {
        ReleaseWindowView view = appService.create(request.getWindowKey(), request.getName());
        return ApiResponse.success(view);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get release window")
    public ApiResponse<ReleaseWindowView> get(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.get(id);
        return ApiResponse.success(view);
    }

    @GetMapping
    @Operation(summary = "List release windows")
    public ApiResponse<List<ReleaseWindowView>> list() {
        List<ReleaseWindowView> list = appService.list();
        return ApiResponse.success(list);
    }

    @GetMapping("/paged")
    @Operation(summary = "List release windows (paged)")
    public ApiPageResponse<List<ReleaseWindowView>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                              @RequestParam(name = "size", defaultValue = "20") int size) {
        List<ReleaseWindowView> all = appService.list();
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<ReleaseWindowView> slice = from >= all.size() ? List.of() : all.subList(from, to);
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish release window")
    public ApiResponse<ReleaseWindowView> publish(@PathVariable("id") String id, @RequestBody(required = false) PublishReleaseWindowRequest request) {
        // 中文注释：发布窗口，需先完成配置
        ReleaseWindowView view = appService.publish(id);
        return ApiResponse.success(view);
    }

    @PutMapping("/{id}/window")
    @Operation(summary = "Configure release window")
    public ApiResponse<ReleaseWindowView> configureWindow(@PathVariable("id") String id, @Valid @RequestBody ConfigureReleaseWindowRequest request) {
        // 中文注释：配置时间窗口，需在冻结前执行
        ReleaseWindowView view = appService.configureWindow(id, request.getStartAtInstant(), request.getEndAtInstant());
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Freeze release window")
    public ApiResponse<ReleaseWindowView> freeze(@PathVariable("id") String id, @RequestBody(required = false) FreezeReleaseWindowRequest request) {
        // 中文注释：冻结窗口，冻结后不可再配置
        ReleaseWindowView view = appService.freeze(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze release window")
    public ApiResponse<ReleaseWindowView> unfreeze(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.unfreeze(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release")
    public ApiResponse<ReleaseWindowView> release(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.release(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close release window")
    public ApiResponse<ReleaseWindowView> close(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.close(id);
        return ApiResponse.success(view);
    }
}
