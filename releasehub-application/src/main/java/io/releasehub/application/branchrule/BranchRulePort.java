package io.releasehub.application.branchrule;

import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleId;

import java.util.List;
import java.util.Optional;

/**
 * BranchRule Port 接口
 */
public interface BranchRulePort {
    void save(BranchRule rule);
    Optional<BranchRule> findById(BranchRuleId id);
    List<BranchRule> findAll();
    void deleteById(BranchRuleId id);
}
