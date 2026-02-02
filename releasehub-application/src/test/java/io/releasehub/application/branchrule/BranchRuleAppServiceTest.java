package io.releasehub.application.branchrule;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;
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
    @DisplayName("存在 BLOCK 且命中时不合规")
    void should_block_when_block_rule_matches() {
        BranchRule block = BranchRule.create("block", "feature/*", BranchRuleType.BLOCK, Instant.now());
        port.save(block);

        assertThat(appService.isCompliant("feature/ITER-1")).isFalse();
    }

    @Test
    @DisplayName("存在 ALLOW 时必须命中允许规则")
    void should_require_allow_rule_when_exists() {
        BranchRule allow = BranchRule.create("allow", "release/*", BranchRuleType.ALLOW, Instant.now());
        port.save(allow);

        assertThat(appService.isCompliant("feature/ITER-1")).isFalse();
        assertThat(appService.isCompliant("release/RW-1")).isTrue();
    }

    @Test
    @DisplayName("get 不存在时抛异常")
    void should_throw_when_rule_not_found() {
        assertThatThrownBy(() -> appService.get("missing"))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("BR_001"));
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
