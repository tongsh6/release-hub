package io.releasehub.application.version;

/**
 * 版本更新结果
 */
public record VersionUpdateResult(
        boolean success,
        String oldVersion,
        String newVersion,
        String diff,
        String errorMessage,
        String filePath
) {
    /**
     * 创建成功结果
     */
    public static VersionUpdateResult success(String oldVersion, String newVersion, String diff, String filePath) {
        return new VersionUpdateResult(true, oldVersion, newVersion, diff, null, filePath);
    }

    /**
     * 创建失败结果
     */
    public static VersionUpdateResult failure(String errorMessage, String filePath) {
        return new VersionUpdateResult(false, null, null, null, errorMessage, filePath);
    }
}
