package io.releasehub.infrastructure.persistence.version;

import io.releasehub.application.version.VersionPolicyPort;
import io.releasehub.domain.version.BumpRule;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import io.releasehub.domain.version.VersionScheme;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * VersionPolicy 数据初始化器
 * <p>
 * 在服务启动时创建内置的 VersionPolicy 数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionPolicyDataInitializer {

    private final VersionPolicyPort versionPolicyPort;

    @PostConstruct
    public void init() {
        log.info("Initializing built-in VersionPolicy data...");

        Instant now = Instant.now();

        // 创建 SemVer MAJOR 策略 - 使用固定 ID
        VersionPolicy majorPolicy = VersionPolicy.rehydrate(
                VersionPolicyId.of("MAJOR"),
                "SemVer MAJOR",
                VersionScheme.SEMVER,
                BumpRule.MAJOR,
                now, now, 0L
        );
        versionPolicyPort.save(majorPolicy);
        log.info("Created VersionPolicy: MAJOR");

        // 创建 SemVer MINOR 策略 - 使用固定 ID
        VersionPolicy minorPolicy = VersionPolicy.rehydrate(
                VersionPolicyId.of("MINOR"),
                "SemVer MINOR",
                VersionScheme.SEMVER,
                BumpRule.MINOR,
                now, now, 0L
        );
        versionPolicyPort.save(minorPolicy);
        log.info("Created VersionPolicy: MINOR");

        // 创建 SemVer PATCH 策略 - 使用固定 ID
        VersionPolicy patchPolicy = VersionPolicy.rehydrate(
                VersionPolicyId.of("PATCH"),
                "SemVer PATCH",
                VersionScheme.SEMVER,
                BumpRule.PATCH,
                now, now, 0L
        );
        versionPolicyPort.save(patchPolicy);
        log.info("Created VersionPolicy: PATCH");

        // 创建日期版本策略 - 使用固定 ID
        VersionPolicy datePolicy = VersionPolicy.rehydrate(
                VersionPolicyId.of("DATE"),
                "Date Version",
                VersionScheme.DATE,
                BumpRule.NONE,
                now, now, 0L
        );
        versionPolicyPort.save(datePolicy);
        log.info("Created VersionPolicy: DATE");

        log.info("VersionPolicy data initialization completed. Total: 4 policies");
    }
}
