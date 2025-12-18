package io.releasehub.interfaces.api.releasewindow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * @author tongshuanglong
 */
@Setter
@Getter
public class CreateReleaseWindowRequest {
    @NotBlank
    @Size(max = 128)
    private String name;

}
