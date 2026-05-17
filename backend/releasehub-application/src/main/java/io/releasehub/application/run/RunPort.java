package io.releasehub.application.run;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.run.Run;

import java.util.List;
import java.util.Optional;

public interface RunPort {
    void save(Run run);
    Optional<Run> findById(String runId);
    List<Run> findAll();
    List<Run> findByWindowKey(String windowKey);
    PageResult<Run> findPaged(String runType, String operator, String windowKey, String repoId, String iterationKey, String status, String groupCode, int page, int size);
}
