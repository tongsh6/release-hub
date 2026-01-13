package io.releasehub.domain.branchrule;

/**
 * 分支规则类型
 */
public enum BranchRuleType {
    /**
     * 允许规则 - 匹配的分支名允许通过
     */
    ALLOW,
    
    /**
     * 阻止规则 - 匹配的分支名将被阻止
     */
    BLOCK
}
