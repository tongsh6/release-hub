package io.releasehub.application.version;

import io.releasehub.domain.version.VersionSource;
import java.util.List;
import java.util.Optional;

/**
 * 版本号提取用例接口
 */
public interface VersionExtractorUseCase {
    
    /**
     * 从仓库中提取版本号
     * @param cloneUrl 仓库克隆地址
     * @param branch 分支名称
     * @return 版本信息，包含版本号和来源
     */
    Optional<VersionInfo> extractVersion(String cloneUrl, String branch);

    /**
     * 从仓库中检查版本号，并返回可追溯诊断。
     */
    default VersionInspection inspectVersion(String cloneUrl, String branch) {
        return extractVersion(cloneUrl, branch)
                .map(info -> VersionInspection.resolved(info.version(), info.source(), branch, List.of()))
                .orElseGet(() -> VersionInspection.unresolved(
                        VersionInspectionError.VERSION_UNRESOLVED,
                        branch,
                        List.of("pom.xml", "gradle.properties"),
                        "未能从 pom.xml 或 gradle.properties 解析版本号"
                ));
    }
    
    /**
     * 版本信息
     */
    record VersionInfo(String version, VersionSource source) {}

    enum VersionInspectionStatus {
        RESOLVED,
        UNRESOLVED
    }

    enum VersionInspectionError {
        VERSION_FILE_MISSING,
        VERSION_DECL_MISSING,
        VERSION_INVALID,
        VERSION_READ_ERROR,
        VERSION_UNRESOLVED
    }

    record VersionInspection(
            VersionInspectionStatus status,
            String version,
            VersionSource source,
            VersionInspectionError errorType,
            String branch,
            List<String> checkedPaths,
            String message
    ) {
        public VersionInspection {
            checkedPaths = checkedPaths == null ? List.of() : List.copyOf(checkedPaths);
        }

        @Override
        public List<String> checkedPaths() {
            return List.copyOf(checkedPaths);
        }

        public static VersionInspection resolved(String version, VersionSource source, String branch, List<String> checkedPaths) {
            return new VersionInspection(
                    VersionInspectionStatus.RESOLVED,
                    version,
                    source,
                    null,
                    branch,
                    checkedPaths,
                    null
            );
        }

        public static VersionInspection unresolved(VersionInspectionError errorType, String branch, List<String> checkedPaths, String message) {
            return new VersionInspection(
                    VersionInspectionStatus.UNRESOLVED,
                    null,
                    null,
                    errorType,
                    branch,
                    checkedPaths,
                    message
            );
        }
    }
}
