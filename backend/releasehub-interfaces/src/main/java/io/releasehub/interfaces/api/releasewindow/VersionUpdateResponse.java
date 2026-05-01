package io.releasehub.interfaces.api.releasewindow;

import lombok.Data;

/**
 * 版本更新响应 DTO
 */
@Data
public class VersionUpdateResponse {
    private String runId;
    private String status;
}
