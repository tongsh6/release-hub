package io.releasehub.interfaces.api.releasewindow;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 版本校验请求 DTO
 */
@Data
public class VersionValidationRequest {
    @NotBlank(message = "版本策略 ID 不能为空")
    private String policyId;

    @NotBlank(message = "当前版本号不能为空")
    private String currentVersion;
}
