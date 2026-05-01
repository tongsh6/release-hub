package io.releasehub.domain.branchrule;

import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 分支规则聚合根
 * <p>
 * 定义分支命名规则，用于验证分支名称是否符合规范。
 * 支持 TEMPLATE（模板匹配，含 glob 和 {placeholder}）和 REGEX（正则匹配）两种模式。
 */
@Getter
public class BranchRule extends BaseEntity<BranchRuleId> {
    private String name;
    private String pattern;
    private BranchRuleType type;
    private String description;
    private BranchRuleScope scope;
    private boolean enabled;

    private BranchRule(BranchRuleId id, String name, String pattern, BranchRuleType type,
                       String description, BranchRuleScope scope, boolean enabled,
                       Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.pattern = pattern;
        this.type = type;
        this.description = description;
        this.scope = scope;
        this.enabled = enabled;
    }

    public static BranchRule create(String name, String pattern, BranchRuleType type,
                                     BranchRuleScope scope, Instant now) {
        return create(name, pattern, type, null, scope, now);
    }

    public static BranchRule create(String name, String pattern, BranchRuleType type,
                                     String description, BranchRuleScope scope, Instant now) {
        validateName(name);
        validatePattern(pattern);
        if (type == null) {
            type = BranchRuleType.TEMPLATE;
        }
        if (scope == null) {
            scope = BranchRuleScope.global();
        }
        return new BranchRule(BranchRuleId.newId(), name, pattern, type, description, scope, true, now, now, 0L);
    }

    public static BranchRule rehydrate(BranchRuleId id, String name, String pattern,
                                        BranchRuleType type, String description,
                                        BranchRuleScope scope, boolean enabled,
                                        Instant createdAt, Instant updatedAt, long version) {
        return new BranchRule(id, name, pattern, type, description, scope, enabled, createdAt, updatedAt, version);
    }

    public void update(String name, String pattern, BranchRuleType type,
                       String description, BranchRuleScope scope, Instant now) {
        validateName(name);
        validatePattern(pattern);
        this.name = name;
        this.pattern = pattern;
        this.type = type != null ? type : BranchRuleType.TEMPLATE;
        this.description = description;
        this.scope = scope != null ? scope : BranchRuleScope.global();
        touch(now);
    }

    public void enable(Instant now) {
        this.enabled = true;
        touch(now);
    }

    public void disable(Instant now) {
        this.enabled = false;
        touch(now);
    }

    /**
     * 检查分支名称是否匹配此规则
     */
    public boolean matches(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return false;
        }
        String regex = type == BranchRuleType.REGEX ? pattern : templateToRegex(pattern);
        return Pattern.compile(regex).matcher(branchName).matches();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ValidationException.brNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.brNameTooLong(128);
        }
    }

    private static void validatePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw ValidationException.brPatternRequired();
        }
        if (pattern.length() > 256) {
            throw ValidationException.brPatternTooLong(256);
        }
        try {
            String regex = templateToRegex(pattern);
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw ValidationException.brPatternInvalid(e.getMessage());
        }
    }

    /**
     * 将模板 pattern 转换为正则表达式。
     * 支持：glob 通配符 (*, **, ?) 和占位符 ({name})
     */
    static String templateToRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            switch (c) {
                case '{':
                    // 占位符 {name} → 匹配不含 / 的一个或多个字符
                    int close = pattern.indexOf('}', i + 1);
                    if (close > i) {
                        regex.append("[^/]+");
                        i = close + 1;
                    } else {
                        regex.append("\\{");
                        i++;
                    }
                    break;
                case '*':
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i += 2;
                    } else {
                        regex.append("[^/]*");
                        i++;
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    i++;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                case '[':
                case ']':
                    regex.append("\\").append(c);
                    i++;
                    break;
                default:
                    regex.append(c);
                    i++;
            }
        }
        regex.append("$");
        return regex.toString();
    }
}
