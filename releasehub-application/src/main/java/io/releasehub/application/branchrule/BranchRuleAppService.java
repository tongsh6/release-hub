package io.releasehub.application.branchrule;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;
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
public class BranchRuleAppService {

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
    public BranchRule create(String name, String pattern, BranchRuleType type) {
        Instant now = Instant.now();
        BranchRule rule = BranchRule.create(name, pattern, type, now);
        branchRulePort.save(rule);
        return rule;
    }

    @Transactional
    public BranchRule update(String id, String name, String pattern, BranchRuleType type) {
        BranchRule rule = get(id);
        rule.update(name, pattern, type, Instant.now());
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

    /**
     * 检查分支名称是否符合规则
     *
     * @param branchName 分支名称
     * @return 是否符合
     */
    @Transactional(readOnly = true)
    public boolean isCompliant(String branchName) {
        List<BranchRule> rules = branchRulePort.findAll();
        if (rules.isEmpty()) {
            return true; // 无规则时默认允许
        }

        // 检查是否有阻止规则匹配
        for (BranchRule rule : rules) {
            if (rule.getType() == BranchRuleType.BLOCK && rule.matches(branchName)) {
                return false;
            }
        }

        // 检查是否有允许规则匹配
        boolean hasAllowRule = rules.stream().anyMatch(r -> r.getType() == BranchRuleType.ALLOW);
        if (hasAllowRule) {
            return rules.stream()
                    .filter(r -> r.getType() == BranchRuleType.ALLOW)
                    .anyMatch(r -> r.matches(branchName));
        }

        return true;
    }
}
