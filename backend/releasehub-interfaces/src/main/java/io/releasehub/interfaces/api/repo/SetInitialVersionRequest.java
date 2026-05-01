package io.releasehub.interfaces.api.repo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetInitialVersionRequest {
    @NotBlank(message = "版本号不能为空")
    private String version;
}
