package io.releasehub.application.run;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.run.Run;

import java.util.List;
import java.util.Optional;

public interface RunPort {
    void save(Run run);
    Optional<Run> findById(String runId);
    List<Run> findAll();
    PageResult<Run> findPaged(String runType, String operator, String windowKey, String repoId, String iterationKey, String status, int page, int size);
}
