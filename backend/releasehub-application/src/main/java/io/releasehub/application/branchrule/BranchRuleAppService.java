package io.releasehub.application.branchrule;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;
import io.releasehub.domain.branchrule.BranchRuleScope;
import io.releasehub.domain.branchrule.BranchRuleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * BranchRule 应用服务
 */
@Service
@RequiredArgsConstructor
public class BranchRuleAppService implements BranchRuleUseCase {

    private final BranchRulePort branchRulePort;

    @Transactional(readOnly = true)
    public List<BranchRule> list() {
        return branchRulePort.findAll();
    }

    @Transactional(readOnly = true)
    public PageResult<BranchRule> listPaged(String name, int page, int size) {
        return branchRulePort.findPaged(name, page, size);
    }

    @Transactional(readOnly = true)
    public BranchRule get(String id) {
        return branchRulePort.findById(BranchRuleId.of(id))
                .orElseThrow(() -> NotFoundException.branchRule(id));
    }

    @Transactional
    public BranchRule create(String name, String pattern, BranchRuleType type,
                              String description, BranchRuleScope scope) {
        Instant now = Instant.now();
        BranchRule rule = BranchRule.create(name, pattern, type, description, scope, now);
        branchRulePort.save(rule);
        return rule;
    }

    @Transactional
    public BranchRule update(String id, String name, String pattern, BranchRuleType type,
                              String description, BranchRuleScope scope) {
        BranchRule rule = get(id);
        rule.update(name, pattern, type, description, scope, Instant.now());
        branchRulePort.save(rule);
        return rule;
    }

    @Transactional
    public void delete(String id) {
        BranchRuleId ruleId = BranchRuleId.of(id);
        branchRulePort.findById(ruleId)
                .orElseThrow(() -> NotFoundException.branchRule(id));
        branchRulePort.deleteById(ruleId);
    }

    @Transactional
    public void enable(String id) {
        BranchRule rule = get(id);
        rule.enable(Instant.now());
        branchRulePort.save(rule);
    }

    @Transactional
    public void disable(String id) {
        BranchRule rule = get(id);
        rule.disable(Instant.now());
        branchRulePort.save(rule);
    }

    /**
     * 检查分支名称是否符合规则
     * 新模型：所有规则都是允许规则，只匹配已启用的规则
     */
    @Transactional(readOnly = true)
    public boolean isCompliant(String branchName) {
        return isCompliant(branchName, null, null);
    }

    /**
     * 检查指定作用域下的分支名称是否符合规则。
     * 规则按 SUB_PROJECT > PROJECT > GLOBAL 解析；存在更具体规则时不再回退到全局规则。
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isCompliant(String branchName, String projectId, String subProjectId) {
        List<BranchRule> enabledRules = branchRulePort.findAllEnabled();
        if (enabledRules.isEmpty()) {
            return true; // 无启用规则时默认允许
        }
        List<BranchRule> scopedRules = enabledRules.stream()
                .filter(rule -> rule.getScope().matches(projectId, subProjectId))
                .toList();
        if (scopedRules.isEmpty()) {
            return true;
        }
        int maxSpecificity = scopedRules.stream()
                .mapToInt(rule -> rule.getScope().specificity())
                .max()
                .orElse(0);
        return scopedRules.stream()
                .filter(rule -> rule.getScope().specificity() == maxSpecificity)
                .anyMatch(rule -> rule.matches(branchName));
    }

    @Transactional(readOnly = true)
    public BranchRuleTestResult test(String pattern, BranchRuleType type, String branchName) {
        if (pattern == null || pattern.isBlank()) {
            return new BranchRuleTestResult(false, null, List.of("Pattern is required"));
        }
        if (branchName == null || branchName.isBlank()) {
            return new BranchRuleTestResult(false, null, List.of("Branch name is required"));
        }
        try {
            // Use a temporary rule to test matching
            BranchRule tempRule = BranchRule.create("_test_", pattern, type,
                    BranchRuleScope.global(), Instant.now());
            boolean matched = tempRule.matches(branchName);
            return new BranchRuleTestResult(matched, null,
                    matched ? List.of() : List.of("Branch name '" + branchName + "' does not match pattern"));
        } catch (Exception e) {
            return new BranchRuleTestResult(false, null, List.of(e.getMessage()));
        }
    }
}
