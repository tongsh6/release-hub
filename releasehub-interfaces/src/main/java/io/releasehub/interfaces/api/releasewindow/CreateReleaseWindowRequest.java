package io.releasehub.interfaces.api.releasewindow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateReleaseWindowRequest {
    @NotBlank
    @Size(max = 128)
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
