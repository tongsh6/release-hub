package io.releasehub.domain.version;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 版本策略聚合根
 * <p>
 * 定义版本号生成和递增策略，支持语义化版本（SemVer）、日期版本等。
 */
@Getter
public class VersionPolicy extends BaseEntity<VersionPolicyId> {
    private final String name;
    private final VersionScheme scheme;
    private final BumpRule bumpRule;

    private VersionPolicy(VersionPolicyId id, String name, VersionScheme scheme, BumpRule bumpRule, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.scheme = scheme;
        this.bumpRule = bumpRule;
    }

    public static VersionPolicy create(String name, VersionScheme scheme, BumpRule bumpRule, Instant now) {
        validateName(name);
        return new VersionPolicy(
                VersionPolicyId.newId(),
                name,
                scheme,
                bumpRule,
                now,
                now,
                0L
        );
    }

    public static VersionPolicy rehydrate(VersionPolicyId id, String name, VersionScheme scheme, BumpRule bumpRule, Instant createdAt, Instant updatedAt, long version) {
        return new VersionPolicy(id, name, scheme, bumpRule, createdAt, updatedAt, version);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ValidationException.vpNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.vpNameTooLong(128);
        }
    }

    /**
     * 根据当前版本推导下一个版本号
     *
     * @param currentVersion 当前版本号
     * @return 下一个版本号
     */
    public String deriveNextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            throw ValidationException.vpCurrentVersionRequired();
        }

        return switch (scheme) {
            case SEMVER -> deriveSemVerNext(currentVersion);
            case DATE -> deriveDateVersion();
            case CUSTOM -> throw BusinessException.vpCustomNotSupported();
        };
    }

    /**
     * 校验版本号格式是否符合策略
     *
     * @param version 版本号
     * @return 是否有效
     */
    public boolean validateVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }

        return switch (scheme) {
            case SEMVER -> validateSemVer(version);
            case DATE -> validateDateVersion(version);
            case CUSTOM -> true; // 自定义策略暂不校验
        };
    }

    private String deriveSemVerNext(String currentVersion) {
        if (!validateSemVer(currentVersion)) {
            throw ValidationException.vpInvalidFormat(currentVersion);
        }

        String[] parts = currentVersion.split("\\.");
        if (parts.length < 3) {
            throw ValidationException.vpInvalidFormat(currentVersion);
        }

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);

        return switch (bumpRule) {
            case MAJOR -> String.format("%d.0.0", major + 1);
            case MINOR -> String.format("%d.%d.0", major, minor + 1);
            case PATCH -> String.format("%d.%d.%d", major, minor, patch + 1);
            case NONE -> currentVersion;
        };
    }

    private String deriveDateVersion() {
        // 日期版本：YYYY.MM.DD 格式
        Instant now = Instant.now();
        java.time.LocalDate date = java.time.LocalDate.ofInstant(now, java.time.ZoneId.systemDefault());
        return String.format("%d.%02d.%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private boolean validateSemVer(String version) {
        // SemVer 格式：MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
        Pattern semverPattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[\\w\\-.]+)?(\\+[\\w\\-.]+)?$");
        return semverPattern.matcher(version).matches();
    }

    private boolean validateDateVersion(String version) {
        // 日期版本格式：YYYY.MM.DD
        Pattern datePattern = Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}$");
        return datePattern.matcher(version).matches();
    }
}
