package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.domain.version.BuildTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 版本更新请求 DTO
 */
@Data
public class VersionUpdateRequest {
    @NotBlank(message = "仓库 ID 不能为空")
    private String repoId;

    @NotBlank(message = "目标版本号不能为空")
    private String targetVersion;

    @NotNull(message = "构建工具类型不能为空")
    private BuildTool buildTool;

    @NotBlank(message = "仓库路径不能为空")
    private String repoPath;

    private String pomPath;

    private String gradlePropertiesPath;
}
