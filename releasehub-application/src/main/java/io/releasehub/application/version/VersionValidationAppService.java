package io.releasehub.application.version;

import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 版本校验应用服务
 * <p>
 * 根据 VersionPolicy 推导目标版本，根据 BranchRule 推导分支名。
 */
@Service
@RequiredArgsConstructor
public class VersionValidationAppService {

    private final VersionPolicyPort versionPolicyPort;

    /**
     * 校验并推导版本号
     *
     * @param policyId 版本策略 ID
     * @param currentVersion 当前版本号（可选）
     * @return 校验结果
     */
    public VersionValidationResult validateVersion(String policyId, String currentVersion) {
        VersionPolicy policy = versionPolicyPort.findById(VersionPolicyId.of(policyId))
                .orElseThrow(() -> NotFoundException.versionPolicy(policyId));

        try {
            String derivedVersion;
            if (currentVersion != null && !currentVersion.isBlank()) {
                // 根据当前版本推导下一个版本
                derivedVersion = policy.deriveNextVersion(currentVersion);
            } else {
                // 如果没有当前版本，使用策略的默认推导（如日期版本）
                derivedVersion = policy.deriveNextVersion("0.0.0");
            }

            // 校验推导出的版本号格式
            if (!policy.validateVersion(derivedVersion)) {
                return VersionValidationResult.failure(
                        "Derived version does not match policy scheme: " + derivedVersion
                );
            }

            return VersionValidationResult.success(derivedVersion, null);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            return VersionValidationResult.failure("Failed to derive version: " + e.getMessage());
        }
    }

    /**
     * 校验版本号格式
     *
     * @param policyId 版本策略 ID
     * @param version 版本号
     * @return 是否有效
     */
    public boolean validateVersionFormat(String policyId, String version) {
        VersionPolicy policy = versionPolicyPort.findById(VersionPolicyId.of(policyId))
                .orElseThrow(() -> NotFoundException.versionPolicy(policyId));

        return policy.validateVersion(version);
    }
}
