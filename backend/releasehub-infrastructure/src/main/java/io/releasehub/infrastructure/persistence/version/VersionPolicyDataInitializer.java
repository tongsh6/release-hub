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
 * 在服务启动时确保内置的 VersionPolicy 数据存在。
 * 使用幂等的 upsert 逻辑，避免重复插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionPolicyDataInitializer {

    private final VersionPolicyPort versionPolicyPort;

    @PostConstruct
    public void init() {
        log.info("Checking built-in VersionPolicy data...");

        int created = 0;
        int skipped = 0;

        Instant now = Instant.now();

        // SemVer MAJOR 策略
        if (ensurePolicy("MAJOR", "SemVer MAJOR", VersionScheme.SEMVER, BumpRule.MAJOR, now)) {
            created++;
        } else {
            skipped++;
        }

        // SemVer MINOR 策略
        if (ensurePolicy("MINOR", "SemVer MINOR", VersionScheme.SEMVER, BumpRule.MINOR, now)) {
            created++;
        } else {
            skipped++;
        }

        // SemVer PATCH 策略
        if (ensurePolicy("PATCH", "SemVer PATCH", VersionScheme.SEMVER, BumpRule.PATCH, now)) {
            created++;
        } else {
            skipped++;
        }

        // 日期版本策略
        if (ensurePolicy("DATE", "Date Version", VersionScheme.DATE, BumpRule.NONE, now)) {
            created++;
        } else {
            skipped++;
        }

        log.info("VersionPolicy initialization completed. Created: {}, Skipped (already exist): {}", created, skipped);
    }

    /**
     * 确保策略存在，如果不存在则创建
     *
     * @return true 如果创建了新策略，false 如果已存在
     */
    private boolean ensurePolicy(String id, String name, VersionScheme scheme, BumpRule bumpRule, Instant now) {
        if (versionPolicyPort.findById(VersionPolicyId.of(id)).isPresent()) {
            log.debug("VersionPolicy {} already exists, skipping", id);
            return false;
        }

        VersionPolicy policy = VersionPolicy.rehydrate(
                VersionPolicyId.of(id),
                name,
                scheme,
                bumpRule,
                now, now, 0L
        );
        versionPolicyPort.save(policy);
        log.info("Created VersionPolicy: {}", id);
        return true;
    }
}
