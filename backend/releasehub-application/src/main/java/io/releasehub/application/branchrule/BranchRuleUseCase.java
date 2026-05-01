package io.releasehub.application.branchrule;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.branchrule.BranchRule;
import io.releasehub.domain.branchrule.BranchRuleType;

import java.util.List;

/**
 * BranchRule 用例接口
 */
public interface BranchRuleUseCase {

    List<BranchRule> list();

    PageResult<BranchRule> listPaged(String name, int page, int size);

    BranchRule get(String id);

    BranchRule create(String name, String pattern, BranchRuleType type);

    BranchRule update(String id, String name, String pattern, BranchRuleType type);

    void delete(String id);

    boolean isCompliant(String branchName);
}