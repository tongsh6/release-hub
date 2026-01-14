package io.releasehub.application.project;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;

import java.util.List;
import java.util.Optional;

/**
 * Port/Gateway：用例层对外部能力的抽象
 */
public interface ProjectPort {
    void save(Project project);

    Optional<Project> findById(ProjectId id);

    List<Project> findAll();

    PageResult<Project> findPaged(String name, String status, int page, int size);

    void deleteById(ProjectId id);
}
