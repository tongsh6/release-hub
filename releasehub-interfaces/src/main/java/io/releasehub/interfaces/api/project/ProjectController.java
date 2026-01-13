package io.releasehub.interfaces.api.project;

import io.releasehub.application.project.ProjectAppService;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.project.Project;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Project API Controller
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project", description = "项目管理 API")
public class ProjectController {

    private final ProjectAppService projectAppService;

    @GetMapping
    @Operation(summary = "获取所有项目")
    public ApiResponse<List<ProjectView>> list() {
        List<Project> projects = projectAppService.list();
        List<ProjectView> views = projects.stream()
                                          .map(this::toView)
                                          .toList();
        return ApiResponse.success(views);
    }

    private ProjectView toView(Project project) {
        return new ProjectView(
                project.getId().value(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getCreatedAt().toString(),
                project.getUpdatedAt().toString()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取项目详情")
    public ApiResponse<ProjectView> get(@PathVariable String id) {
        Project project = projectAppService.get(id);
        return ApiResponse.success(toView(project));
    }

    @PostMapping
    @Operation(summary = "创建项目")
    public ApiResponse<ProjectView> create(@RequestBody CreateProjectRequest request) {
        Project project = projectAppService.create(request.name(), request.description());
        return ApiResponse.success(toView(project));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新项目")
    public ApiResponse<ProjectView> update(@PathVariable String id, @RequestBody UpdateProjectRequest request) {
        Project project = projectAppService.update(id, request.name(), request.description());
        return ApiResponse.success(toView(project));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档项目")
    public ApiResponse<Void> archive(@PathVariable String id) {
        projectAppService.archive(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目")
    public ApiResponse<Void> delete(@PathVariable String id) {
        projectAppService.delete(id);
        return ApiResponse.success(null);
    }

    public record ProjectView(
            String id,
            String name,
            String description,
            String status,
            String createdAt,
            String updatedAt
    ) {
    }

    public record CreateProjectRequest(
            String name,
            String description
    ) {
    }

    public record UpdateProjectRequest(
            String name,
            String description
    ) {
    }
}
