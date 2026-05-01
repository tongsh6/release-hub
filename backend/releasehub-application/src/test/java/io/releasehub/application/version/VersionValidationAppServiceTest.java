package io.releasehub.application.version;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleType;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.version.BumpRule;
import io.releasehub.domain.version.VersionPolicy;
import io.releasehub.domain.version.VersionPolicyId;
import io.releasehub.domain.version.VersionScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VersionValidationAppService 测试")
class VersionValidationAppServiceTest {

    private InMemoryVersionPolicyPort versionPolicyPort;
    private InMemoryReleaseWindowPort releaseWindowPort;
    private InMemoryBranchRuleUseCase branchRuleUseCase;
    private VersionValidationAppService appService;

    @BeforeEach
    void setUp() {
        versionPolicyPort = new InMemoryVersionPolicyPort();
        releaseWindowPort = new InMemoryReleaseWindowPort();
        branchRuleUseCase = new InMemoryBranchRuleUseCase();
        appService = new VersionValidationAppService(versionPolicyPort, releaseWindowPort, branchRuleUseCase);

        VersionPolicy patchPolicy = VersionPolicy.rehydrate(
                VersionPolicyId.of("PATCH"),
                "Patch",
                VersionScheme.SEMVER,
                BumpRule.PATCH,
                Instant.now(),
                Instant.now(),
                0L
        );
        versionPolicyPort.save(patchPolicy);
    }

    @Test
    @DisplayName("优先推导 release 前缀分支")
    void shouldPreferReleasePattern() {
        ReleaseWindow window = ReleaseWindow.createDraft("RW-20260304", "window", null, null, "G1", Instant.now());
        releaseWindowPort.save(window);

        branchRuleUseCase.setRules(List.of(
                BranchRule.create("feature", "feature/*", BranchRuleType.ALLOW, Instant.now()),
                BranchRule.create("release", "release/*", BranchRuleType.ALLOW, Instant.now())
        ));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isTrue();
        assertThat(result.derivedVersion()).isEqualTo("1.2.4");
        assertThat(result.derivedBranch()).isEqualTo("release/RW-20260304");
    }

    @Test
    @DisplayName("无允许规则时回退到 release 分支")
    void shouldFallbackToReleaseBranchWhenNoAllowRules() {
        ReleaseWindow window = ReleaseWindow.createDraft("RW-20260304", "window", null, null, "G1", Instant.now());
        releaseWindowPort.save(window);

        branchRuleUseCase.setRules(List.of(
                BranchRule.create("main-block", "main", BranchRuleType.BLOCK, Instant.now())
        ));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isTrue();
        assertThat(result.derivedBranch()).isEqualTo("release/RW-20260304");
    }

    @Test
    @DisplayName("推导分支不合规时返回失败")
    void shouldFailWhenDerivedBranchIsNotCompliant() {
        ReleaseWindow window = ReleaseWindow.createDraft("RW-20260304", "window", null, null, "G1", Instant.now());
        releaseWindowPort.save(window);

        branchRuleUseCase.setRules(List.of(
                BranchRule.create("block-release", "release/*", BranchRuleType.BLOCK, Instant.now())
        ));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("Derived branch does not match branch rules");
    }

    private static class InMemoryVersionPolicyPort implements VersionPolicyPort {
        private final Map<String, VersionPolicy> policies = new HashMap<>();

        @Override
        public void save(VersionPolicy policy) {
            policies.put(policy.getId().value(), policy);
        }

        @Override
        public Optional<VersionPolicy> findById(VersionPolicyId id) {
            return Optional.ofNullable(policies.get(id.value()));
        }

        @Override
        public List<VersionPolicy> findAll() {
            return new ArrayList<>(policies.values());
        }

        @Override
        public PageResult<VersionPolicy> findPaged(String keyword, int page, int size) {
            List<VersionPolicy> list = findAll();
            return new PageResult<>(list, list.size());
        }

        @Override
        public void deleteById(VersionPolicyId id) {
            policies.remove(id.value());
        }
    }

    private static class InMemoryReleaseWindowPort implements ReleaseWindowPort {
        private final Map<String, ReleaseWindow> windows = new HashMap<>();

        @Override
        public void save(ReleaseWindow releaseWindow) {
            windows.put(releaseWindow.getId().value(), releaseWindow);
        }

        @Override
        public Optional<ReleaseWindow> findById(ReleaseWindowId id) {
            return Optional.ofNullable(windows.get(id.value()));
        }

        @Override
        public List<ReleaseWindow> findAll() {
            return new ArrayList<>(windows.values());
        }

        @Override
        public PageResult<ReleaseWindow> findPaged(String name, ReleaseWindowStatus status, int page, int size) {
            List<ReleaseWindow> list = findAll();
            return new PageResult<>(list, list.size());
        }
    }

    private static class InMemoryBranchRuleUseCase implements BranchRuleUseCase {
        private List<BranchRule> rules = new ArrayList<>();

        void setRules(List<BranchRule> rules) {
            this.rules = new ArrayList<>(rules);
        }

        @Override
        public List<BranchRule> list() {
            return new ArrayList<>(rules);
        }

        @Override
        public PageResult<BranchRule> listPaged(String name, int page, int size) {
            List<BranchRule> list = list();
            return new PageResult<>(list, list.size());
        }

        @Override
        public BranchRule get(String id) {
            return rules.stream().filter(rule -> rule.getId().value().equals(id)).findFirst().orElse(null);
        }

        @Override
        public BranchRule create(String name, String pattern, BranchRuleType type) {
            BranchRule rule = BranchRule.create(name, pattern, type, Instant.now());
            rules.add(rule);
            return rule;
        }

        @Override
        public BranchRule update(String id, String name, String pattern, BranchRuleType type) {
            BranchRule existing = get(id);
            if (existing == null) {
                return null;
            }
            existing.update(name, pattern, type, Instant.now());
            return existing;
        }

        @Override
        public void delete(String id) {
            rules = rules.stream().filter(rule -> !rule.getId().value().equals(id)).toList();
        }

        @Override
        public boolean isCompliant(String branchName) {
            if (rules.isEmpty()) {
                return true;
            }

            for (BranchRule rule : rules) {
                if (rule.getType() == BranchRuleType.BLOCK && rule.matches(branchName)) {
                    return false;
                }
            }

            boolean hasAllowRule = rules.stream().anyMatch(rule -> rule.getType() == BranchRuleType.ALLOW);
            if (hasAllowRule) {
                return rules.stream()
                        .filter(rule -> rule.getType() == BranchRuleType.ALLOW)
                        .anyMatch(rule -> rule.matches(branchName));
            }

            return true;
        }
    }
}
