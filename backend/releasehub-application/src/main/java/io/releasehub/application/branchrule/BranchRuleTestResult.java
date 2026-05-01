package io.releasehub.application.branchrule;

import java.util.List;

/**
 * 分支规则测试结果
 */
public record BranchRuleTestResult(
        boolean ok,
        String rendered,
        List<String> errors
) {
}
