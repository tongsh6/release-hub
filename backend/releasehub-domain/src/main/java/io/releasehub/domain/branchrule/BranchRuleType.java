package io.releasehub.domain.branchrule;

/**
 * 分支规则模式类型
 * <p>
 * TEMPLATE: 模板匹配（glob 通配符 * / ** / ? 或占位符 {name}）
 * REGEX: 正则匹配（直接使用正则表达式）
 */
public enum BranchRuleType {
    TEMPLATE,
    REGEX
}
