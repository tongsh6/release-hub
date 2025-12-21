package io.releasehub.interfaces.api.window;

import io.releasehub.application.window.AttachAppService;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/windows")
@RequiredArgsConstructor
public class AttachController {
    private final AttachAppService attachAppService;

    @PostMapping("/{id}/attach")
    public ApiResponse<List<String>> attach(@PathVariable("id") String windowId, @RequestBody AttachRequest request) {
        var list = attachAppService.attach(windowId, request.getIterationKeys());
        return ApiResponse.success(list.stream().map(x -> x.getIterationKey().value()).toList());
    }

    @PostMapping("/{id}/detach")
    public ApiResponse<Boolean> detach(@PathVariable("id") String windowId, @RequestBody DetachRequest request) {
        attachAppService.detach(windowId, request.getIterationKey());
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/iterations")
    public ApiResponse<List<WindowIterationView>> list(@PathVariable("id") String windowId) {
        var list = attachAppService.list(windowId);
        return ApiResponse.success(list.stream().map(x -> new WindowIterationView(x.getIterationKey().value(), x.getAttachAt())).toList());
    }

    @GetMapping("/{id}/iterations/paged")
    public ApiPageResponse<List<WindowIterationView>> listPaged(@PathVariable("id") String windowId,
                                                                @RequestParam(name = "page", defaultValue = "0") int page,
                                                                @RequestParam(name = "size", defaultValue = "20") int size) {
        var all = attachAppService.list(windowId).stream()
                                  .map(x -> new WindowIterationView(x.getIterationKey().value(), x.getAttachAt()))
                                  .toList();
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<WindowIterationView> slice = from >= all.size() ? List.<WindowIterationView>of() : all.subList(from, to);
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }

    @Data
    public static class AttachRequest {
        private List<String> iterationKeys;
    }

    @Data
    public static class DetachRequest {
        private String iterationKey;
    }

    @Data
    public static class WindowIterationView {
        private final String iterationKey;
        private final java.time.Instant attachAt;
    }
}
