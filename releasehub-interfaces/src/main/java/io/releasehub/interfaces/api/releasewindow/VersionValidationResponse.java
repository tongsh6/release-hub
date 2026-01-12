package io.releasehub.interfaces.api.releasewindow;

import lombok.Data;

/**
 * 版本校验响应 DTO
 */
@Data
public class VersionValidationResponse {
    private boolean valid;
    private String derivedVersion;
    private String derivedBranch;
    private String errorMessage;
}
