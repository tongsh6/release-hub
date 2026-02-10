package io.releasehub.application.version;

/**
 * 版本校验结果
 */
public record VersionValidationResult(
        boolean valid,
        String derivedVersion,
        String derivedBranch,
        String errorMessage
) {
    /**
     * 创建成功结果
     */
    public static VersionValidationResult success(String derivedVersion, String derivedBranch) {
        return new VersionValidationResult(true, derivedVersion, derivedBranch, null);
    }

    /**
     * 创建失败结果
     */
    public static VersionValidationResult failure(String errorMessage) {
        return new VersionValidationResult(false, null, null, errorMessage);
    }
}
