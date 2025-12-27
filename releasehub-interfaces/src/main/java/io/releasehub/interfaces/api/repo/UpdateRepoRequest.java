package io.releasehub.interfaces.api.repo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRepoRequest {
    @NotNull
    private Long gitlabProjectId;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 512)
    private String cloneUrl;

    @NotBlank
    @Size(max = 128)
    private String defaultBranch;

    private boolean monoRepo;
}
