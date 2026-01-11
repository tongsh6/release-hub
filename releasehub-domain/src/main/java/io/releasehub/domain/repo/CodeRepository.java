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
    private Long gitlabProjectId;
    private String name;
    private String cloneUrl;
    private boolean monoRepo;
    private String defaultBranch;
    private int branchCount;
    private int activeBranchCount;
    private int nonCompliantBranchCount;
    private int mrCount;
    private int openMrCount;
    private int mergedMrCount;
    private int closedMrCount;
    private Instant lastSyncAt;

    public CodeRepository(RepoId id, ProjectId projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.projectId = projectId;
        this.gitlabProjectId = gitlabProjectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
        this.branchCount = branchCount;
        this.activeBranchCount = activeBranchCount;
        this.nonCompliantBranchCount = nonCompliantBranchCount;
        this.mrCount = mrCount;
        this.openMrCount = openMrCount;
        this.mergedMrCount = mergedMrCount;
        this.closedMrCount = closedMrCount;
        this.lastSyncAt = lastSyncAt;
    }

    private CodeRepository(RepoId id, ProjectId projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.projectId = projectId;
        this.gitlabProjectId = gitlabProjectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
        this.branchCount = branchCount;
        this.activeBranchCount = activeBranchCount;
        this.nonCompliantBranchCount = nonCompliantBranchCount;
        this.mrCount = mrCount;
        this.openMrCount = openMrCount;
        this.mergedMrCount = mergedMrCount;
        this.closedMrCount = closedMrCount;
        this.lastSyncAt = lastSyncAt;
    }

    public static CodeRepository rehydrate(RepoId id, ProjectId projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt, long version) {
        return new CodeRepository(id, projectId, gitlabProjectId, name, cloneUrl, defaultBranch, monoRepo, branchCount, activeBranchCount, nonCompliantBranchCount, mrCount, openMrCount, mergedMrCount, closedMrCount, lastSyncAt, createdAt, updatedAt, version);
    }

    private CodeRepository(RepoId id, ProjectId projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant now) {
        super(id, now);
        if (projectId == null) {
            throw new BizException("REPO_PROJECT_REQUIRED", "Project ID is required");
        }
        if (gitlabProjectId == null) {
            throw new BizException("REPO_GITLAB_ID_REQUIRED", "GitLab Project ID is required");
        }
        validateName(name);
        validateUrl(cloneUrl);
        validateBranch(defaultBranch);

        this.projectId = projectId;
        this.gitlabProjectId = gitlabProjectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
        this.branchCount = 0;
        this.activeBranchCount = 0;
        this.nonCompliantBranchCount = 0;
        this.mrCount = 0;
        this.openMrCount = 0;
        this.mergedMrCount = 0;
        this.closedMrCount = 0;
        this.lastSyncAt = null;
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

    public static CodeRepository create(ProjectId projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant now) {
        return new CodeRepository(RepoId.newId(), projectId, gitlabProjectId, name, cloneUrl, defaultBranch, monoRepo, now);
    }

    public void changeDefaultBranch(String branch, Instant now) {
        validateBranch(branch);
        this.defaultBranch = branch;
        touch(now);
    }

    public void updateStatistics(int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant now) {
        this.branchCount = branchCount;
        this.activeBranchCount = activeBranchCount;
        this.nonCompliantBranchCount = nonCompliantBranchCount;
        this.mrCount = mrCount;
        this.openMrCount = openMrCount;
        this.mergedMrCount = mergedMrCount;
        this.closedMrCount = closedMrCount;
        this.lastSyncAt = now;
        touch(now);
    }

    public void update(Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, Instant now) {
        if (gitlabProjectId == null) {
            throw new BizException("REPO_GITLAB_ID_REQUIRED", "GitLab Project ID is required");
        }
        validateName(name);
        validateUrl(cloneUrl);
        validateBranch(defaultBranch);
        this.gitlabProjectId = gitlabProjectId;
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.monoRepo = monoRepo;
        touch(now);
    }
}
