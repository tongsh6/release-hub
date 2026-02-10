package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.domain.version.BuildTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量版本更新请求 DTO
 */
@Data
public class BatchVersionUpdateRequest {
    @NotEmpty(message = "仓库列表不能为空")
    @Valid
    private List<RepoVersionUpdate> repositories;

    @NotBlank(message = "目标版本号不能为空")
    private String targetVersion;

    @Data
    public static class RepoVersionUpdate {
        @NotBlank(message = "仓库 ID 不能为空")
        private String repoId;

        @NotNull(message = "构建工具类型不能为空")
        private BuildTool buildTool;

        @NotBlank(message = "仓库路径不能为空")
        private String repoPath;

        private String pomPath;

        private String gradlePropertiesPath;
    }
}
