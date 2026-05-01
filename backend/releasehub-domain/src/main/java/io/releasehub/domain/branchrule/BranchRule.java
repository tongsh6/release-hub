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
 */
@Getter
public class BranchRule extends BaseEntity<BranchRuleId> {
    private String name;
    private String pattern;
    private BranchRuleType type;

    private BranchRule(BranchRuleId id, String name, String pattern, BranchRuleType type, 
                       Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.pattern = pattern;
        this.type = type;
    }

    public static BranchRule create(String name, String pattern, BranchRuleType type, Instant now) {
        validateName(name);
        validatePattern(pattern);
        if (type == null) {
            type = BranchRuleType.ALLOW;
        }
        return new BranchRule(BranchRuleId.newId(), name, pattern, type, now, now, 0L);
    }

    public static BranchRule rehydrate(BranchRuleId id, String name, String pattern, BranchRuleType type,
                                       Instant createdAt, Instant updatedAt, long version) {
        return new BranchRule(id, name, pattern, type, createdAt, updatedAt, version);
    }

    public void update(String name, String pattern, BranchRuleType type, Instant now) {
        validateName(name);
        validatePattern(pattern);
        this.name = name;
        this.pattern = pattern;
        this.type = type != null ? type : BranchRuleType.ALLOW;
        touch(now);
    }

    /**
     * 检查分支名称是否匹配此规则
     *
     * @param branchName 分支名称
     * @return 是否匹配
     */
    public boolean matches(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return false;
        }
        // 将 glob 模式转换为正则表达式
        String regex = globToRegex(this.pattern);
        return Pattern.compile(regex).matcher(branchName).matches();
    }

    /**
     * 检查分支名称是否被此规则允许
     *
     * @param branchName 分支名称
     * @return 是否允许
     */
    public boolean isAllowed(String branchName) {
        boolean matched = matches(branchName);
        if (type == BranchRuleType.ALLOW) {
            return matched;
        } else {
            return !matched;
        }
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
        // 验证模式是否有效
        try {
            String regex = globToRegex(pattern);
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw ValidationException.brPatternInvalid(e.getMessage());
        }
    }

    /**
     * 将 glob 模式转换为正则表达式
     * 支持 * 和 ** 通配符
     */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        // ** 匹配任意字符（包括 /）
                        regex.append(".*");
                        i++;
                    } else {
                        // * 匹配除 / 外的任意字符
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
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
                case '{':
                case '}':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }
}
