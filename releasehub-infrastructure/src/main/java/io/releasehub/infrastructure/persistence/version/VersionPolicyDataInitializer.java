package io.releasehub.infrastructure.persistence.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.domain.version.BumpRule;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionScheme;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * VersionPolicy 数据初始化器
 * <p>
 * 在服务启动时创建测试用的 VersionPolicy 数据（内存存储）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionPolicyDataInitializer {

    private final VersionPolicyPort versionPolicyPort;

    @PostConstruct
    public void init() {
        log.info("Initializing test VersionPolicy data...");

        // 创建 SemVer MAJOR 策略
        VersionPolicy majorPolicy = VersionPolicy.create(
                "SemVer MAJOR",
                VersionScheme.SEMVER,
                BumpRule.MAJOR,
                Instant.now()
        );
        versionPolicyPort.save(majorPolicy);
        log.info("Created VersionPolicy: {} (MAJOR)", majorPolicy.getId().value());

        // 创建 SemVer MINOR 策略
        VersionPolicy minorPolicy = VersionPolicy.create(
                "SemVer MINOR",
                VersionScheme.SEMVER,
                BumpRule.MINOR,
                Instant.now()
        );
        versionPolicyPort.save(minorPolicy);
        log.info("Created VersionPolicy: {} (MINOR)", minorPolicy.getId().value());

        // 创建 SemVer PATCH 策略
        VersionPolicy patchPolicy = VersionPolicy.create(
                "SemVer PATCH",
                VersionScheme.SEMVER,
                BumpRule.PATCH,
                Instant.now()
        );
        versionPolicyPort.save(patchPolicy);
        log.info("Created VersionPolicy: {} (PATCH)", patchPolicy.getId().value());

        // 创建日期版本策略
        VersionPolicy datePolicy = VersionPolicy.create(
                "Date Version",
                VersionScheme.DATE,
                BumpRule.NONE,
                Instant.now()
        );
        versionPolicyPort.save(datePolicy);
        log.info("Created VersionPolicy: {} (DATE)", datePolicy.getId().value());

        log.info("VersionPolicy data initialization completed. Total: 4 policies");
    }
}
