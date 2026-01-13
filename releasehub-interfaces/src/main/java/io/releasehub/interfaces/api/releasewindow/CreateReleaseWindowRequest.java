package io.releasehub.interfaces.api.releasewindow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author tongshuanglong
 */
@Setter
@Getter
public class CreateReleaseWindowRequest {
    @NotBlank
    @Size(max = 200)
    private String name;
    
    @Size(max = 2000)
    private String description;
    
    private Instant plannedReleaseAt;
}
