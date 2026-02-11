package io.releasehub.interfaces.api.repo;

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

    private boolean monoRepo;

    @Size(max = 50)
    private String initialVersion;
}
