package io.releasehub.interfaces.api.repo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRepoRequest {
    @NotBlank
    private String projectId;

    @NotNull
    private Long gitlabProjectId;
    
    @NotBlank
    private String name;
    
    @NotBlank
    private String cloneUrl;
    
    @NotBlank
    private String defaultBranch;
    
    private boolean monoRepo;
}
