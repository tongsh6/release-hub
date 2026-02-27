package io.releasehub.interfaces.api.repo;

import io.releasehub.domain.repo.CodeRepository;
import lombok.Data;

import java.time.Instant;

/**
 * @author tongshuanglong
 */
@Data
public class CodeRepositoryView {
    private String id;
    private String name;
    private String cloneUrl;
    private String defaultBranch;
    private String groupCode;
    private String repoType;
    private String gitProvider;
    private String gitTokenMasked;
    private boolean monoRepo;
    private int branchCount;
    private int activeBranchCount;
    private int nonCompliantBranchCount;
    private int mrCount;
    private int openMrCount;
    private int mergedMrCount;
    private int closedMrCount;
    private Instant lastSyncAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static CodeRepositoryView fromDomain(CodeRepository domain) {
        if (domain == null) {
            return null;
        }
        CodeRepositoryView view = new CodeRepositoryView();
        view.setId(domain.getId().value());
        view.setName(domain.getName());
        view.setCloneUrl(domain.getCloneUrl());
        view.setDefaultBranch(domain.getDefaultBranch());
        view.setGroupCode(domain.getGroupCode());
        view.setRepoType(domain.getRepoType().name());
        view.setGitProvider(domain.getGitProvider().name());
        view.setGitTokenMasked(maskGitToken(domain.getGitToken()));
        view.setMonoRepo(domain.isMonoRepo());
        view.setBranchCount(domain.getBranchCount());
        view.setActiveBranchCount(domain.getActiveBranchCount());
        view.setNonCompliantBranchCount(domain.getNonCompliantBranchCount());
        view.setMrCount(domain.getMrCount());
        view.setOpenMrCount(domain.getOpenMrCount());
        view.setMergedMrCount(domain.getMergedMrCount());
        view.setClosedMrCount(domain.getClosedMrCount());
        view.setLastSyncAt(domain.getLastSyncAt());
        view.setCreatedAt(domain.getCreatedAt());
        view.setUpdatedAt(domain.getUpdatedAt());
        return view;
    }

    private static String maskGitToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        int visible = Math.min(4, trimmed.length());
        return trimmed.substring(0, visible) + "****";
    }
}
