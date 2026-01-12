package io.releasehub.application.project;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.project.Project;
import io.releasehub.domain.project.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectAppService {
    private final ProjectPort projectPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Project create(String name, String description) {
        Project project = Project.create(name, description, Instant.now(clock));
        projectPort.save(project);
        return project;
    }

    public Project get(String id) {
        return projectPort.findById(ProjectId.of(id))
                .orElseThrow(() -> NotFoundException.project(id));
    }

    public List<Project> list() {
        return projectPort.findAll();
    }
}
