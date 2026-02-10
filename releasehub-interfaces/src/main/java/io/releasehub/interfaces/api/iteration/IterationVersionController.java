package io.releasehub.interfaces.api.iteration;

import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "迭代版本管理", description = "迭代仓库版本相关接口")
@RestController
@RequestMapping("/api/v1/iterations")
@RequiredArgsConstructor
public class IterationVersionController {

    private final IterationRepoPort iterationRepoPort;

    @Operation(summary = "获取迭代的仓库版本信息列表")
    @GetMapping("/{iterationKey}/versions")
    public ApiResponse<List<IterationRepoVersionInfo>> listVersions(@PathVariable String iterationKey) {
        List<IterationRepoVersionInfo> versions = iterationRepoPort.listVersionInfo(iterationKey);
        return ApiResponse.success(versions);
    }

    @Operation(summary = "获取迭代的单个仓库版本信息")
    @GetMapping("/{iterationKey}/repos/{repoId}/version")
    public ApiResponse<IterationRepoVersionInfo> getVersion(
            @PathVariable String iterationKey,
            @PathVariable String repoId) {
        return iterationRepoPort.getVersionInfo(iterationKey, repoId)
                                .map(ApiResponse::success)
                                .orElse(ApiResponse.success(null));
    }
}
