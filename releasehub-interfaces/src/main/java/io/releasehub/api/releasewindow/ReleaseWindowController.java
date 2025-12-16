package io.releasehub.api.releasewindow;

import io.releasehub.common.response.ApiResponse;
import io.releasehub.releasewindow.ReleaseWindow;
import io.releasehub.releasewindow.ReleaseWindowAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/release-windows")
public class ReleaseWindowController {

    private final ReleaseWindowAppService appService;

    public ReleaseWindowController(ReleaseWindowAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public ApiResponse<ReleaseWindowView> create(@Valid @RequestBody CreateReleaseWindowRequest request) {
        ReleaseWindow rw = appService.create(request.getName());
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseWindowView> get(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.get(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @GetMapping
    public ApiResponse<List<ReleaseWindowView>> list() {
        List<ReleaseWindowView> list = appService.list().stream()
                                                 .map(ReleaseWindowView::from)
                                                 .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ReleaseWindowView> submit(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.submit(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @PutMapping("/{id}/window")
    public ApiResponse<ReleaseWindowView> configureWindow(@PathVariable("id") String id, @Valid @RequestBody ConfigureReleaseWindowRequest request) {
        ReleaseWindow rw = appService.configureWindow(id, request.getStartAtInstant(), request.getEndAtInstant());
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<ReleaseWindowView> freeze(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.freeze(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<ReleaseWindowView> unfreeze(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.unfreeze(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<ReleaseWindowView> release(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.release(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ReleaseWindowView> close(@PathVariable("id") String id) {
        ReleaseWindow rw = appService.close(id);
        return ApiResponse.success(ReleaseWindowView.from(rw));
    }
}
