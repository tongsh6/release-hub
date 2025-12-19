package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.releasewindow.ReleaseWindowAppService;
import io.releasehub.application.releasewindow.ReleaseWindowView;
import io.releasehub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
public class ReleaseWindowController {

    private final ReleaseWindowAppService appService;

    @PostMapping
    public ApiResponse<ReleaseWindowView> create(@Valid @RequestBody CreateReleaseWindowRequest request) {
        ReleaseWindowView view = appService.create(request.getName());
        return ApiResponse.success(view);
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseWindowView> get(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.get(id);
        return ApiResponse.success(view);
    }

    @GetMapping
    public ApiResponse<List<ReleaseWindowView>> list() {
        List<ReleaseWindowView> list = appService.list();
        return ApiResponse.success(list);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ReleaseWindowView> publish(@PathVariable("id") String id, @RequestBody(required = false) PublishReleaseWindowRequest request) {
        // 中文注释：发布窗口，需先完成配置
        ReleaseWindowView view = appService.publish(id);
        return ApiResponse.success(view);
    }

    @PutMapping("/{id}/window")
    public ApiResponse<ReleaseWindowView> configureWindow(@PathVariable("id") String id, @Valid @RequestBody ConfigureReleaseWindowRequest request) {
        // 中文注释：配置时间窗口，需在冻结前执行
        ReleaseWindowView view = appService.configureWindow(id, request.getStartAtInstant(), request.getEndAtInstant());
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<ReleaseWindowView> freeze(@PathVariable("id") String id, @RequestBody(required = false) FreezeReleaseWindowRequest request) {
        // 中文注释：冻结窗口，冻结后不可再配置
        ReleaseWindowView view = appService.freeze(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<ReleaseWindowView> unfreeze(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.unfreeze(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/release")
    public ApiResponse<ReleaseWindowView> release(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.release(id);
        return ApiResponse.success(view);
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ReleaseWindowView> close(@PathVariable("id") String id) {
        ReleaseWindowView view = appService.close(id);
        return ApiResponse.success(view);
    }
}
