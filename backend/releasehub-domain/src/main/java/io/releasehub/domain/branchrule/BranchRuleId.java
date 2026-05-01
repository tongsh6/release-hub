package io.releasehub.domain.branchrule;

import java.util.UUID;

/**
 * 分支规则 ID 值对象
 */
public record BranchRuleId(String value) {

    public static BranchRuleId of(String value) {
        return new BranchRuleId(value);
    }

    public static BranchRuleId newId() {
        return new BranchRuleId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
