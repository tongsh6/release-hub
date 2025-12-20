package io.releasehub.domain.repo;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.project.ProjectId;
import lombok.Getter;

import java.time.Instant;

/**
 * @author tongshuanglong
 */
@Getter
public class CodeRepository extends BaseEntity<RepoId> {
    private final ProjectId projectId;
    private final String name;
    private final String cloneUrl;
    private final boolean monoRepo;
    private String defaultBranch;

    public CodeRepository(RepoId id, ProjectId projectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.projectId = projectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
    }

    private CodeRepository(RepoId id, ProjectId projectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant now) {
        super(id, now);
        if (projectId == null) {
            throw new BizException("REPO_PROJECT_REQUIRED", "Project ID is required");
        }
        validateName(name);
        validateUrl(cloneUrl);
        validateBranch(defaultBranch);

        this.projectId = projectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("REPO_NAME_REQUIRED", "Repository name is required");
        }
        if (name.length() > 128) {
            throw new BizException("REPO_NAME_TOO_LONG", "Repository name is too long (max 128)");
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("REPO_URL_REQUIRED", "Clone URL is required");
        }
        if (url.length() > 512) {
            throw new BizException("REPO_URL_TOO_LONG", "Clone URL is too long (max 512)");
        }
    }

    private void validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new BizException("REPO_BRANCH_REQUIRED", "Default branch is required");
        }
        if (branch.length() > 128) {
            throw new BizException("REPO_BRANCH_TOO_LONG", "Default branch is too long (max 128)");
        }
    }

    public static CodeRepository create(ProjectId projectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant now) {
        return new CodeRepository(RepoId.newId(), projectId, name, cloneUrl, defaultBranch, monoRepo, now);
    }

    public void changeDefaultBranch(String branch, Instant now) {
        validateBranch(branch);
        this.defaultBranch = branch;
        touch(now);
    }
}
