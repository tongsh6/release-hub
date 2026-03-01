package io.releasehub.application.version;

/**
 * 版本号推导用例接口
 */
public interface VersionDeriverUseCase {
    
    /**
     * 从基准版本推导开发版本
     * 规则：中间版本号 +1，末尾版本号归零，添加 -SNAPSHOT
     * 例如：1.2.3 → 1.3.0-SNAPSHOT
     *      1.2.3-SNAPSHOT → 1.3.0-SNAPSHOT
     */
    String deriveDevVersion(String baseVersion);
    
    /**
     * 从开发版本推导目标版本
     * 规则：移除 -SNAPSHOT 后缀
     * 例如：1.3.0-SNAPSHOT → 1.3.0
     */
    String deriveTargetVersion(String devVersion);
    
    /**
     * 检查是否为 SNAPSHOT 版本
     */
    boolean isSnapshot(String version);
    
    /**
     * 移除 SNAPSHOT 后缀
     */
    String removeSnapshot(String version);
    
    /**
     * 添加 SNAPSHOT 后缀
     */
    String addSnapshot(String version);
    
    /**
     * 比较版本号
     * @return 负数表示 v1 < v2，0 表示相等，正数表示 v1 > v2
     */
    int compareVersions(String v1, String v2);
}