package io.releasehub.application.branchrule;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleScope;
import io.releasehub.domain.branchrule.BranchRuleType;

import java.util.List;

/**
 * BranchRule 用例接口
 */
public interface BranchRuleUseCase {

    List<BranchRule> list();

    PageResult<BranchRule> listPaged(String name, int page, int size);

    BranchRule get(String id);

    BranchRule create(String name, String pattern, BranchRuleType type,
                      String description, BranchRuleScope scope);

    BranchRule update(String id, String name, String pattern, BranchRuleType type,
                      String description, BranchRuleScope scope);

    void delete(String id);

    void enable(String id);

    void disable(String id);

    boolean isCompliant(String branchName);

    default boolean isCompliant(String branchName, String projectId, String subProjectId) {
        return isCompliant(branchName);
    }

    BranchRuleTestResult test(String pattern, BranchRuleType type, String branchName);
}
