package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.run.RunAppService;
import io.releasehub.application.run.RunAppService.RepoVersionUpdateInfo;
import io.releasehub.application.version.VersionValidationAppService;
import io.releasehub.application.version.VersionValidationResult;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.run.Run;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 版本更新 API 控制器
 */
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 版本更新")
public class VersionUpdateController {

    private final RunAppService runAppService;
    private final VersionValidationAppService versionValidationAppService;

    @PostMapping("/{id}/execute/version-update")
    @Operation(summary = "执行版本更新", description = "对指定发布窗口关联的仓库执行版本号更新")
    public ApiResponse<VersionUpdateResponse> executeVersionUpdate(
            @PathVariable("id") String windowId,
            @Valid @RequestBody VersionUpdateRequest request
    ) {
        // 获取当前用户（暂时使用固定值，后续可以从 SecurityContext 获取）
        String operator = "system"; // TODO: 从 SecurityContext 获取当前用户

        Run run = runAppService.executeVersionUpdate(
                windowId,
                request.getRepoId(),
                request.getTargetVersion(),
                request.getBuildTool(),
                request.getRepoPath(),
                request.getPomPath(),
                request.getGradlePropertiesPath(),
                operator
        );

        VersionUpdateResponse response = new VersionUpdateResponse();
        response.setRunId(run.getId());
        response.setStatus(run.getFinishedAt() != null ? "COMPLETED" : "RUNNING");

        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/execute/batch-version-update")
    @Operation(summary = "批量执行版本更新", description = "对指定发布窗口关联的多个仓库执行版本号更新")
    public ApiResponse<VersionUpdateResponse> executeBatchVersionUpdate(
            @PathVariable("id") String windowId,
            @Valid @RequestBody BatchVersionUpdateRequest request
    ) {
        // 获取当前用户（暂时使用固定值，后续可以从 SecurityContext 获取）
        String operator = "system"; // TODO: 从 SecurityContext 获取当前用户

        // 转换为应用层模型
        List<RepoVersionUpdateInfo> repoInfos = request.getRepositories().stream()
                .map(repo -> new RepoVersionUpdateInfo(
                        repo.getRepoId(),
                        repo.getBuildTool(),
                        repo.getRepoPath(),
                        repo.getPomPath(),
                        repo.getGradlePropertiesPath()
                ))
                .collect(Collectors.toList());

        Run run = runAppService.executeBatchVersionUpdate(
                windowId,
                repoInfos,
                request.getTargetVersion(),
                operator
        );

        VersionUpdateResponse response = new VersionUpdateResponse();
        response.setRunId(run.getId());
        response.setStatus(run.getFinishedAt() != null ? "COMPLETED" : "RUNNING");

        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "版本校验", description = "根据 VersionPolicy 推导并校验目标版本号")
    public ApiResponse<VersionValidationResponse> validateVersion(
            @PathVariable("id") String windowId,
            @Valid @RequestBody VersionValidationRequest request
    ) {
        VersionValidationResult result = versionValidationAppService.validateVersion(
                request.getPolicyId(),
                request.getCurrentVersion()
        );

        VersionValidationResponse response = new VersionValidationResponse();
        response.setValid(result.valid());
        response.setDerivedVersion(result.derivedVersion());
        response.setDerivedBranch(result.derivedBranch());
        response.setErrorMessage(result.errorMessage());

        return ApiResponse.success(response);
    }
}
