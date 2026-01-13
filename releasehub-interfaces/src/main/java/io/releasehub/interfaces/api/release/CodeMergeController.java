package io.releasehub.interfaces.api.release;

import io.releasehub.application.release.CodeMergeService;
import io.releasehub.application.release.CodeMergeService.CodeMergeResult;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "代码合并", description = "代码合并相关接口")
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
public class CodeMergeController {

    private final CodeMergeService codeMergeService;

    @Operation(summary = "合并指定迭代的代码到 release 分支")
    @PostMapping("/{windowId}/iterations/{iterationKey}/merge")
    public ApiResponse<List<CodeMergeResult>> mergeIteration(
            @PathVariable String windowId,
            @PathVariable String iterationKey) {
        List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(windowId, iterationKey);
        return ApiResponse.success(results);
    }

    @Operation(summary = "批量合并所有迭代的代码到 release 分支")
    @PostMapping("/{windowId}/merge")
    public ApiResponse<List<CodeMergeResult>> mergeAll(@PathVariable String windowId) {
        List<CodeMergeResult> results = codeMergeService.mergeAllFeaturesToRelease(windowId);
        return ApiResponse.success(results);
    }
}
