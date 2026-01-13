package io.releasehub.application.project;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectAppService {
    private final ProjectPort projectPort;

    @Transactional(readOnly = true)
    public List<Project> list() {
        return projectPort.findAll();
    }

    @Transactional(readOnly = true)
    public Project get(String id) {
        return projectPort.findById(ProjectId.of(id))
                .orElseThrow(() -> NotFoundException.project(id));
    }

    @Transactional
    public Project create(String name, String description) {
        Instant now = Instant.now();
        Project project = Project.create(name, description, now);
        projectPort.save(project);
        return project;
    }

    @Transactional
    public Project update(String id, String name, String description) {
        Project project = get(id);
        project.rename(name, Instant.now());
        // Note: description update would require adding updateDescription method to Project
        projectPort.save(project);
        return project;
    }

    @Transactional
    public void archive(String id) {
        Project project = get(id);
        project.archive(Instant.now());
        projectPort.save(project);
    }

    @Transactional
    public void delete(String id) {
        ProjectId projectId = ProjectId.of(id);
        projectPort.findById(projectId)
                .orElseThrow(() -> NotFoundException.project(id));
        projectPort.deleteById(projectId);
    }
}
