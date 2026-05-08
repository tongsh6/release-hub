package io.releasehub.interfaces.api.repo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRepoRequest {
    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 512)
    private String cloneUrl;

    @NotBlank
    @Size(max = 64)
    private String groupCode;

    @Size(max = 128)
    private String defaultBranch;

    private String repoType;

    @Size(max = 20)
    private String gitProvider;

    @Size(max = 500)
    @Schema(description = "Git 平台访问令牌")
    private String gitAccessToken;

    private boolean monoRepo;

    @Size(max = 50)
    private String initialVersion;
}
