package io.releasehub.domain.branchrule;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BranchRule 领域实体测试")
class BranchRuleTest {

    @Test
    @DisplayName("创建规则时名称必填")
    void should_fail_when_name_blank() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> BranchRule.create(" ", "feature/*", BranchRuleType.ALLOW, Instant.now()));
        assertEquals("BR_002", ex.getCode());
    }

    @Test
    @DisplayName("创建规则时 pattern 必填")
    void should_fail_when_pattern_blank() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> BranchRule.create("Feature", " ", BranchRuleType.ALLOW, Instant.now()));
        assertEquals("BR_004", ex.getCode());
    }

    @Test
    @DisplayName("创建规则时 type 为空默认 ALLOW")
    void should_default_type_when_null() {
        BranchRule rule = BranchRule.create("Feature", "feature/*", null, Instant.now());
        assertThat(rule.getType()).isEqualTo(BranchRuleType.ALLOW);
    }

    @Test
    @DisplayName("glob 匹配 * 与 ** 的差异")
    void should_match_glob_patterns() {
        BranchRule star = BranchRule.create("Star", "feature/*", BranchRuleType.ALLOW, Instant.now());
        BranchRule doubleStar = BranchRule.create("DoubleStar", "feature/**", BranchRuleType.ALLOW, Instant.now());

        assertThat(star.matches("feature/ITER-1")).isTrue();
        assertThat(star.matches("feature/ITER-1/sub")).isFalse();

        assertThat(doubleStar.matches("feature/ITER-1")).isTrue();
        assertThat(doubleStar.matches("feature/ITER-1/sub")).isTrue();
    }

    @Test
    @DisplayName("pattern 过长时抛出异常")
    void should_fail_when_pattern_too_long() {
        String longPattern = "a".repeat(257);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> BranchRule.create("Feature", longPattern, BranchRuleType.ALLOW, Instant.now()));
        assertEquals("BR_005", ex.getCode());
    }
}
