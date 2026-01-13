package io.releasehub.application.version;

import org.springframework.stereotype.Component;

/**
 * 版本号推导工具
 */
@Component
public class VersionDeriver {
    
    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
    
    /**
     * 从基准版本推导开发版本
     * 规则：中间版本号 +1，末尾版本号归零，添加 -SNAPSHOT
     * 例如：1.2.3 → 1.3.0-SNAPSHOT
     *      1.2.3-SNAPSHOT → 1.3.0-SNAPSHOT
     */
    public String deriveDevVersion(String baseVersion) {
        String cleanVersion = removeSnapshot(baseVersion);
        String[] parts = cleanVersion.split("\\.");
        
        if (parts.length < 2) {
            // 简单版本号处理
            return cleanVersion + ".1" + SNAPSHOT_SUFFIX;
        }
        
        try {
            int minor = Integer.parseInt(parts[1]) + 1;
            return parts[0] + "." + minor + ".0" + SNAPSHOT_SUFFIX;
        } catch (NumberFormatException e) {
            // 非数字版本号，直接追加
            return cleanVersion + SNAPSHOT_SUFFIX;
        }
    }
    
    /**
     * 从开发版本推导目标版本
     * 规则：移除 -SNAPSHOT 后缀
     * 例如：1.3.0-SNAPSHOT → 1.3.0
     */
    public String deriveTargetVersion(String devVersion) {
        return removeSnapshot(devVersion);
    }
    
    /**
     * 检查是否为 SNAPSHOT 版本
     */
    public boolean isSnapshot(String version) {
        return version != null && version.toUpperCase().endsWith(SNAPSHOT_SUFFIX.toUpperCase());
    }
    
    /**
     * 移除 SNAPSHOT 后缀
     */
    public String removeSnapshot(String version) {
        if (version == null) {
            return null;
        }
        if (version.toUpperCase().endsWith(SNAPSHOT_SUFFIX.toUpperCase())) {
            return version.substring(0, version.length() - SNAPSHOT_SUFFIX.length());
        }
        return version;
    }
    
    /**
     * 添加 SNAPSHOT 后缀
     */
    public String addSnapshot(String version) {
        if (version == null) {
            return null;
        }
        if (isSnapshot(version)) {
            return version;
        }
        return version + SNAPSHOT_SUFFIX;
    }
    
    /**
     * 比较版本号
     * @return 负数表示 v1 < v2，0 表示相等，正数表示 v1 > v2
     */
    public int compareVersions(String v1, String v2) {
        String clean1 = removeSnapshot(v1);
        String clean2 = removeSnapshot(v2);
        
        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");
        
        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int num1 = i < parts1.length ? parsePartSafe(parts1[i]) : 0;
            int num2 = i < parts2.length ? parsePartSafe(parts2[i]) : 0;
            
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        
        // 基本版本相同，比较 SNAPSHOT
        boolean snap1 = isSnapshot(v1);
        boolean snap2 = isSnapshot(v2);
        
        if (snap1 && !snap2) return -1;  // SNAPSHOT < 正式版
        if (!snap1 && snap2) return 1;   // 正式版 > SNAPSHOT
        
        return 0;
    }
    
    private int parsePartSafe(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
