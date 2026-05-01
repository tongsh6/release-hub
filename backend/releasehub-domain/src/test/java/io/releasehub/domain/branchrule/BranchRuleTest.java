package io.releasehub.domain.branchrule;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BranchRule 聚合根测试")
class BranchRuleTest {

    private static final Instant NOW = Instant.now();

    @Nested
    @DisplayName("创建规则")
    class Create {

        @Test
        @DisplayName("name 为空应抛异常")
        void should_reject_blank_name() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> BranchRule.create(" ", "feature/*",
                            BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW));
            assertEquals("BR_002", ex.getCode());
        }

        @Test
        @DisplayName("pattern 为空应抛异常")
        void should_reject_blank_pattern() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> BranchRule.create("Feature", " ",
                            BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW));
            assertEquals("BR_004", ex.getCode());
        }

        @Test
        @DisplayName("type 为空默认 TEMPLATE")
        void should_default_type_when_null() {
            BranchRule rule = BranchRule.create("Feature", "feature/*",
                    null, BranchRuleScope.global(), NOW);
            assertThat(rule.getType()).isEqualTo(BranchRuleType.TEMPLATE);
        }

        @Test
        @DisplayName("pattern 过长时抛异常")
        void should_fail_when_pattern_too_long() {
            String longPattern = "a".repeat(257);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> BranchRule.create("Feature", longPattern,
                            BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW));
            assertEquals("BR_005", ex.getCode());
        }

        @Test
        @DisplayName("默认启用")
        void should_be_enabled_by_default() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            assertThat(rule.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("应支持 scope 为 GLOBAL")
        void should_support_global_scope() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            assertThat(rule.getScope().getLevel()).isEqualTo(BranchRuleScope.ScopeLevel.GLOBAL);
            assertThat(rule.getScope().isGlobal()).isTrue();
        }

        @Test
        @DisplayName("应支持 scope 为 PROJECT")
        void should_support_project_scope() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE,
                    BranchRuleScope.project("proj-1"), NOW);

            assertThat(rule.getScope().getLevel()).isEqualTo(BranchRuleScope.ScopeLevel.PROJECT);
            assertThat(rule.getScope().getProjectId()).isEqualTo("proj-1");
        }

        @Test
        @DisplayName("应支持 description 可选")
        void should_support_optional_description() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, "用于 feature 分支",
                    BranchRuleScope.global(), NOW);

            assertThat(rule.getDescription()).isEqualTo("用于 feature 分支");
        }
    }

    @Nested
    @DisplayName("启用/禁用")
    class EnableDisable {

        @Test
        @DisplayName("应支持禁用规则")
        void should_disable() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            rule.disable(NOW);
            assertThat(rule.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("应支持重新启用规则")
        void should_enable() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);
            rule.disable(NOW);
            rule.enable(NOW);
            assertThat(rule.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("模板匹配")
    class TemplateMatching {

        @Test
        @DisplayName("glob 匹配 * 与 ** 的差异")
        void should_match_glob_patterns() {
            BranchRule star = BranchRule.create("Star", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);
            BranchRule doubleStar = BranchRule.create("DoubleStar", "feature/**",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            assertThat(star.matches("feature/ITER-1")).isTrue();
            assertThat(star.matches("feature/ITER-1/sub")).isFalse();

            assertThat(doubleStar.matches("feature/ITER-1")).isTrue();
            assertThat(doubleStar.matches("feature/ITER-1/sub")).isTrue();
        }

        @Test
        @DisplayName("TEMPLATE 占位符 {name} 匹配任意不含 / 的字符串")
        void should_match_template_placeholders() {
            BranchRule rule = BranchRule.create("feature", "feature/{key}",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            assertThat(rule.matches("feature/ITER-1")).isTrue();
            assertThat(rule.matches("feature/HOTFIX-2")).isTrue();
            assertThat(rule.matches("release/v1.0.0")).isFalse();
            assertThat(rule.matches("feature/")).isFalse();
        }

        @Test
        @DisplayName("TEMPLATE 多占位符")
        void should_match_multiple_placeholders() {
            BranchRule rule = BranchRule.create("feature", "feature/{project}/{key}",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            assertThat(rule.matches("feature/PRJ/ITER-1")).isTrue();
            assertThat(rule.matches("feature/PRJ")).isFalse();
        }
    }

    @Nested
    @DisplayName("正则匹配")
    class RegexMatching {

        @Test
        @DisplayName("REGEX 类型应直接使用正则匹配")
        void should_match_regex_directly() {
            BranchRule rule = BranchRule.create("release", "release/[A-Z]+-\\d+",
                    BranchRuleType.REGEX, BranchRuleScope.global(), NOW);

            assertThat(rule.matches("release/RW-123")).isTrue();
            assertThat(rule.matches("release/rw-123")).isFalse();
        }
    }

    @Nested
    @DisplayName("重建（从持久化加载）")
    class Rehydrate {

        @Test
        @DisplayName("应正确重建包含所有新字段的聚合根")
        void should_rehydrate_with_all_fields() {
            BranchRule rule = BranchRule.rehydrate(
                    BranchRuleId.of("id-1"), "feature", "feature/*",
                    BranchRuleType.TEMPLATE, "用于 feature 分支",
                    BranchRuleScope.global(), true,
                    NOW, NOW, 1L);

            assertThat(rule.getName()).isEqualTo("feature");
            assertThat(rule.getType()).isEqualTo(BranchRuleType.TEMPLATE);
            assertThat(rule.getDescription()).isEqualTo("用于 feature 分支");
            assertThat(rule.getScope().getLevel()).isEqualTo(BranchRuleScope.ScopeLevel.GLOBAL);
            assertThat(rule.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("更新规则")
    class Update {

        @Test
        @DisplayName("应支持更新 scope 和 description")
        void should_update_scope_and_description() {
            BranchRule rule = BranchRule.create("test", "feature/*",
                    BranchRuleType.TEMPLATE, BranchRuleScope.global(), NOW);

            rule.update("renamed", "release/*", BranchRuleType.REGEX,
                    "updated desc", BranchRuleScope.project("p2"), NOW);

            assertThat(rule.getName()).isEqualTo("renamed");
            assertThat(rule.getType()).isEqualTo(BranchRuleType.REGEX);
            assertThat(rule.getDescription()).isEqualTo("updated desc");
            assertThat(rule.getScope().getProjectId()).isEqualTo("p2");
        }
    }
}
