package io.releasehub.application.version;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleType;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 版本校验应用服务
 * <p>
 * 根据 VersionPolicy 推导目标版本，根据 BranchRule 推导分支名。
 */
@Service
public class VersionValidationAppService {

    private static final String DEFAULT_RELEASE_PREFIX = "release/";

    private final VersionPolicyPort versionPolicyPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final BranchRuleUseCase branchRuleUseCase;

    public VersionValidationAppService(
            VersionPolicyPort versionPolicyPort,
            ReleaseWindowPort releaseWindowPort,
            BranchRuleUseCase branchRuleUseCase
    ) {
        this.versionPolicyPort = versionPolicyPort;
        this.releaseWindowPort = releaseWindowPort;
        this.branchRuleUseCase = branchRuleUseCase;
    }

    /**
     * 校验并推导版本号
     *
     * @param windowId 发布窗口 ID
     * @param policyId 版本策略 ID
     * @param currentVersion 当前版本号（可选）
     * @return 校验结果
     */
    public VersionValidationResult validateVersion(String windowId, String policyId, String currentVersion) {
        VersionPolicy policy = versionPolicyPort.findById(VersionPolicyId.of(policyId))
                .orElseThrow(() -> NotFoundException.versionPolicy(policyId));

        try {
            String derivedVersion = policy.deriveNextVersion(currentVersion);
            String derivedBranch = deriveBranch(windowId);

            // 校验推导出的版本号格式
            if (!policy.validateVersion(derivedVersion)) {
                return VersionValidationResult.failure(
                        "Derived version does not match policy scheme: " + derivedVersion
                );
            }

            if (!branchRuleUseCase.isCompliant(derivedBranch)) {
                return VersionValidationResult.failure(
                        "Derived branch does not match branch rules: " + derivedBranch
                );
            }

            return VersionValidationResult.success(derivedVersion, derivedBranch);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            return VersionValidationResult.failure("Failed to derive version: " + e.getMessage());
        }
    }

    private String deriveBranch(String windowId) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));

        String windowKey = releaseWindow.getWindowKey();
        String fallbackBranch = DEFAULT_RELEASE_PREFIX + windowKey;

        List<BranchRule> allowRules = branchRuleUseCase.list().stream()
                .filter(rule -> rule.getType() == BranchRuleType.ALLOW)
                .sorted(Comparator
                        .comparing((BranchRule rule) -> !rule.getPattern().startsWith(DEFAULT_RELEASE_PREFIX))
                        .thenComparingInt(rule -> wildcardCount(rule.getPattern()))
                        .thenComparingInt(rule -> rule.getPattern().length()))
                .toList();

        for (BranchRule rule : allowRules) {
            String candidate = buildCandidateBranch(rule.getPattern(), windowKey);
            if (branchRuleUseCase.isCompliant(candidate)) {
                return candidate;
            }
        }

        return fallbackBranch;
    }

    private int wildcardCount(String pattern) {
        return pattern.length() - pattern.replace("*", "").length();
    }

    private String buildCandidateBranch(String pattern, String windowKey) {
        String candidate = pattern;
        if (candidate.contains("**")) {
            candidate = candidate.replace("**", windowKey);
        }
        if (candidate.contains("*")) {
            candidate = candidate.replace("*", windowKey);
        }
        if (candidate.contains("?")) {
            candidate = candidate.replace("?", windowKey);
        }
        return candidate;
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
