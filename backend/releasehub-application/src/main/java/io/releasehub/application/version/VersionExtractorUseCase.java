package io.releasehub.application.version;

import io.releasehub.domain.version.VersionSource;
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
     * 版本信息
     */
    record VersionInfo(String version, VersionSource source) {}
}