package io.releasehub.infrastructure.persistence.project;

import io.releasehub.application.project.ProjectPort;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter：基础设施层对 Port 的实现
 */
@Repository
public class ProjectPersistenceAdapter implements ProjectPort {

    private final Map<ProjectId, Project> store = new ConcurrentHashMap<>();

    @Override
    public void save(Project project) {
        store.put(project.getId(), project);
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Project> findAll() {
        return new ArrayList<>(store.values());
    }
}
