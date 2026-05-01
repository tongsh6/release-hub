package io.releasehub.application.branchrule;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;
import io.releasehub.domain.branchrule.BranchRuleScope;
import io.releasehub.domain.branchrule.BranchRuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BranchRuleAppService 测试")
class BranchRuleAppServiceTest {

    private InMemoryPort port;
    private BranchRuleAppService appService;

    @BeforeEach
    void setUp() {
        port = new InMemoryPort();
        appService = new BranchRuleAppService(port);
    }

    @Test
    @DisplayName("无规则时默认合规")
    void should_allow_when_no_rules() {
        assertThat(appService.isCompliant("feature/ITER-1")).isTrue();
    }

    @Test
    @DisplayName("存在启用规则且命中时合规")
    void should_allow_when_rule_matches() {
        BranchRule rule = BranchRule.create("feature", "feature/*",
                BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now());
        port.save(rule);

        assertThat(appService.isCompliant("feature/ITER-1")).isTrue();
    }

    @Test
    @DisplayName("存在启用规则但未命中时不合规")
    void should_block_when_no_rule_matches() {
        BranchRule rule = BranchRule.create("release", "release/*",
                BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now());
        port.save(rule);

        assertThat(appService.isCompliant("feature/ITER-1")).isFalse();
    }

    @Test
    @DisplayName("禁用规则不影响合规检查")
    void should_ignore_disabled_rules() {
        BranchRule rule = BranchRule.create("feature", "feature/*",
                BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now());
        rule.disable(Instant.now());
        port.save(rule);

        assertThat(appService.isCompliant("feature/ITER-1")).isTrue();
    }

    @Test
    @DisplayName("get 不存在时抛异常")
    void should_throw_when_rule_not_found() {
        assertThatThrownBy(() -> appService.get("missing"))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("BR_001"));
    }

    @Test
    @DisplayName("应支持启用和禁用规则")
    void should_enable_and_disable() {
        BranchRule rule = BranchRule.create("test", "feature/*",
                BranchRuleType.TEMPLATE, BranchRuleScope.global(), Instant.now());
        port.save(rule);

        appService.disable(rule.getId().value());
        assertThat(port.findById(rule.getId()).get().isEnabled()).isFalse();

        appService.enable(rule.getId().value());
        assertThat(port.findById(rule.getId()).get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("test 方法应返回匹配结果")
    void should_test_pattern() {
        BranchRuleTestResult result = appService.test("release/*",
                BranchRuleType.TEMPLATE, "release/RW-1");

        assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("test 方法不匹配时返回错误")
    void should_test_pattern_non_match() {
        BranchRuleTestResult result = appService.test("release/*",
                BranchRuleType.TEMPLATE, "feature/IT-1");

        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    static class InMemoryPort implements BranchRulePort {
        private final Map<String, BranchRule> store = new HashMap<>();

        @Override
        public void save(BranchRule rule) {
            store.put(rule.getId().value(), rule);
        }

        @Override
        public Optional<BranchRule> findById(BranchRuleId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<BranchRule> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<BranchRule> findAllEnabled() {
            return store.values().stream()
                    .filter(BranchRule::isEnabled)
                    .collect(Collectors.toList());
        }

        @Override
        public PageResult<BranchRule> findPaged(String name, int page, int size) {
            List<BranchRule> list = findAll();
            return new PageResult<>(list, list.size());
        }

        @Override
        public void deleteById(BranchRuleId id) {
            store.remove(id.value());
        }
    }
}
