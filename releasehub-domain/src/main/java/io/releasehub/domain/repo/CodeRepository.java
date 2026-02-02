package io.releasehub.domain.repo;

import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

/**
 * @author tongshuanglong
 */
@Getter
public class CodeRepository extends BaseEntity<RepoId> {
    private String name;
    private String cloneUrl;
    private boolean monoRepo;
    private String defaultBranch;
    private String groupCode;
    private int branchCount;
    private int activeBranchCount;
    private int nonCompliantBranchCount;
    private int mrCount;
    private int openMrCount;
    private int mergedMrCount;
    private int closedMrCount;
    private Instant lastSyncAt;

    public CodeRepository(RepoId id, String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.groupCode = groupCode;
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

    private CodeRepository(RepoId id, String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.groupCode = groupCode;
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

    public static CodeRepository rehydrate(RepoId id, String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, int branchCount, int activeBranchCount, int nonCompliantBranchCount, int mrCount, int openMrCount, int mergedMrCount, int closedMrCount, Instant lastSyncAt, Instant createdAt, Instant updatedAt, long version) {
        return new CodeRepository(id, name, cloneUrl, defaultBranch, groupCode, monoRepo, branchCount, activeBranchCount, nonCompliantBranchCount, mrCount, openMrCount, mergedMrCount, closedMrCount, lastSyncAt, createdAt, updatedAt, version);
    }

    private CodeRepository(RepoId id, String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, Instant now) {
        super(id, now);
        validateName(name);
        validateUrl(cloneUrl);
        validateBranch(defaultBranch);
        validateGroupCode(groupCode);

        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.groupCode = groupCode;
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
            throw ValidationException.repoNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.repoNameTooLong(128);
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw ValidationException.repoUrlRequired();
        }
        if (url.length() > 512) {
            throw ValidationException.repoUrlTooLong(512);
        }
    }

    private void validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            throw ValidationException.repoBranchRequired();
        }
        if (branch.length() > 128) {
            throw ValidationException.repoBranchTooLong(128);
        }
    }

    private void validateGroupCode(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw ValidationException.groupCodeRequired();
        }
        if (groupCode.length() > 64) {
            throw ValidationException.groupCodeTooLong(64);
        }
    }

    public static CodeRepository create(String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, Instant now) {
        return new CodeRepository(RepoId.newId(), name, cloneUrl, defaultBranch, groupCode, monoRepo, now);
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

    public void update(String name, String cloneUrl, String defaultBranch, String groupCode, boolean monoRepo, Instant now) {
        validateName(name);
        validateUrl(cloneUrl);
        validateBranch(defaultBranch);
        validateGroupCode(groupCode);
        this.name = name;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch;
        this.groupCode = groupCode;
        this.monoRepo = monoRepo;
        touch(now);
    }
}
