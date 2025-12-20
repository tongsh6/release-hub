package io.releasehub.interfaces.api.window;

import io.releasehub.application.window.AttachAppService;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Data
    public static class AttachRequest {
        private List<String> iterationKeys;
    }

    @Data
    public static class DetachRequest {
        private String iterationKey;
    }
}
