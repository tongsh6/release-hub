package io.releasehub.domain.version;

/**
 * 版本号来源
 */
public enum VersionSource {
    POM,      // Maven pom.xml
    GRADLE,   // Gradle build.gradle / gradle.properties
    MANUAL,   // 手动设置
    SYSTEM,   // 系统存储
    REPO      // 从代码仓库实时获取
}
