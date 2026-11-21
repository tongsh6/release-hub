package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.window.AttachAppService;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.window.WindowIteration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 迭代管理")
public class AttachController {
    private final AttachAppService attachAppService;

    @PostMapping("/{id}/attach")
    @Operation(summary = "关联迭代")
    public ApiResponse<List<WindowIterationView>> attach(@PathVariable("id") String windowId,
                                                         @Valid @RequestBody AttachRequest request) {
        var bindings = attachAppService.attach(windowId, request.getIterationKeys());
        return ApiResponse.success(bindings.stream().map(WindowIterationView::from).toList());
    }

    @PostMapping("/{id}/detach")
    @Operation(summary = "解除迭代关联")
    public ApiResponse<Boolean> detach(@PathVariable("id") String windowId,
                                        @Valid @RequestBody DetachRequest request) {
        attachAppService.detach(windowId, request.getIterationKey());
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/iterations")
    @Operation(summary = "查询窗口迭代")
    public ApiResponse<List<WindowIterationView>> list(@PathVariable("id") String windowId) {
        return ApiResponse.success(attachAppService.list(windowId).stream().map(WindowIterationView::from).toList());
    }

    @GetMapping("/{id}/iterations/paged")
    @Operation(summary = "分页查询窗口迭代")
    public ApiPageResponse<List<WindowIterationView>> listPaged(@PathVariable("id") String windowId,
                                                               @RequestParam(name = "page", defaultValue = "1") int page,
                                                               @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = attachAppService.listPaged(windowId, page, size);
        List<WindowIterationView> views = result.items().stream().map(WindowIterationView::from).toList();
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @Data
    public static class AttachRequest {
        @NotEmpty(message = "迭代列表不能为空")
        private List<@NotBlank String> iterationKeys;
    }

    @Data
    public static class DetachRequest {
        @NotBlank
        private String iterationKey;
    }

    @Data
    public static class WindowIterationView {
        private final String iterationKey;
        private final java.time.Instant attachAt;
        private final String releaseBranch;
        private final Boolean branchCreated;
        private final java.time.Instant lastMergeAt;

        public static WindowIterationView from(WindowIteration binding) {
            return new WindowIterationView(binding.getIterationKey().value(), binding.getAttachAt(),
                    binding.getReleaseBranch(), binding.getBranchCreated(), binding.getLastMergeAt());
        }
    }
}
