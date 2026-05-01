package io.releasehub.application.version;

import io.releasehub.application.branchrule.BranchRuleTestResult;
import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleScope;
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
                BranchRule.create("feature", "feature/*", BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now()),
                BranchRule.create("release", "release/*", BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now())
        ));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isTrue();
        assertThat(result.derivedVersion()).isEqualTo("1.2.4");
        assertThat(result.derivedBranch()).isEqualTo("release/RW-20260304");
    }

    @Test
    @DisplayName("无启用规则时回退到 release 分支")
    void shouldFallbackToReleaseBranchWhenNoEnabledRules() {
        ReleaseWindow window = ReleaseWindow.createDraft("RW-20260304", "window", null, null, "G1", Instant.now());
        releaseWindowPort.save(window);

        // 所有规则都禁用
        BranchRule disabledRule = BranchRule.create("feature", "feature/*",
                BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now());
        disabledRule.disable(Instant.now());
        branchRuleUseCase.setRules(List.of(disabledRule));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isTrue();
        assertThat(result.derivedBranch()).isEqualTo("release/RW-20260304");
    }

    @Test
    @DisplayName("有匹配规则时使用规则衍生分支（非 release 回退）")
    void shouldUseMatchingRuleWhenAvailable() {
        ReleaseWindow window = ReleaseWindow.createDraft("RW-20260304", "window", null, null, "G1", Instant.now());
        releaseWindowPort.save(window);

        // 只允许 feature/ 开头的分支 — 衍生器应使用 feature 规则而非回退到 release/
        branchRuleUseCase.setRules(List.of(
                BranchRule.create("only-feature", "feature/*",
                        BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now())
        ));

        VersionValidationResult result = appService.validateVersion(window.getId().value(), "PATCH", "1.2.3");

        assertThat(result.valid()).isTrue();
        assertThat(result.derivedBranch()).isEqualTo("feature/RW-20260304");
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
        public BranchRule create(String name, String pattern, BranchRuleType type,
                                  String description, BranchRuleScope scope) {
            BranchRule rule = BranchRule.create(name, pattern, type, description, scope, Instant.now());
            rules.add(rule);
            return rule;
        }

        @Override
        public BranchRule update(String id, String name, String pattern, BranchRuleType type,
                                  String description, BranchRuleScope scope) {
            BranchRule existing = get(id);
            if (existing == null) return null;
            existing.update(name, pattern, type, description, scope, Instant.now());
            return existing;
        }

        @Override
        public void delete(String id) {
            rules = rules.stream().filter(rule -> !rule.getId().value().equals(id)).toList();
        }

        @Override
        public void enable(String id) {
            BranchRule rule = get(id);
            if (rule != null) rule.enable(Instant.now());
        }

        @Override
        public void disable(String id) {
            BranchRule rule = get(id);
            if (rule != null) rule.disable(Instant.now());
        }

        @Override
        public boolean isCompliant(String branchName) {
            List<BranchRule> enabled = rules.stream().filter(BranchRule::isEnabled).toList();
            if (enabled.isEmpty()) return true;
            return enabled.stream().anyMatch(r -> r.matches(branchName));
        }

        @Override
        public BranchRuleTestResult test(String pattern, BranchRuleType type, String branchName) {
            return new BranchRuleTestResult(false, null, List.of("not implemented in stub"));
        }
    }
}
