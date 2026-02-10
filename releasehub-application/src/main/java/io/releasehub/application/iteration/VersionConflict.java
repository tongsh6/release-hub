package io.releasehub.application.iteration;

import io.releasehub.domain.version.ConflictType;
import lombok.Builder;
import lombok.Getter;

/**
 * 版本冲突信息 DTO
 */
@Getter
@Builder
public class VersionConflict {
    private String repoId;
    private String iterationKey;
    private String systemVersion;    // 系统记录的版本
    private String repoVersion;      // 仓库实际的版本
    private ConflictType conflictType;
    private String message;

    public boolean hasConflict() {
        return conflictType != null;
    }
    
    public static VersionConflict noConflict(String repoId, String iterationKey, String version) {
        return VersionConflict.builder()
                .repoId(repoId)
                .iterationKey(iterationKey)
                .systemVersion(version)
                .repoVersion(version)
                .build();
    }
    
    public static VersionConflict mismatch(String repoId, String iterationKey, String systemVersion, String repoVersion) {
        return VersionConflict.builder()
                .repoId(repoId)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .conflictType(ConflictType.MISMATCH)
                .message("系统版本与仓库版本不一致")
                .build();
    }
    
    public static VersionConflict repoAhead(String repoId, String iterationKey, String systemVersion, String repoVersion) {
        return VersionConflict.builder()
                .repoId(repoId)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .conflictType(ConflictType.REPO_AHEAD)
                .message("仓库版本高于系统记录")
                .build();
    }
    
    public static VersionConflict systemAhead(String repoId, String iterationKey, String systemVersion, String repoVersion) {
        return VersionConflict.builder()
                .repoId(repoId)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .conflictType(ConflictType.SYSTEM_AHEAD)
                .message("系统版本高于仓库版本")
                .build();
    }
}
