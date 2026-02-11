package io.releasehub.infrastructure.persistence.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "code_repository")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeRepositoryJpaEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(name = "clone_url", nullable = false, length = 512)
    private String cloneUrl;
    @Column(name = "default_branch")
    private String defaultBranch;
    @Column(name = "group_code")
    private String groupCode;
    @Column(name = "repo_type", length = 32, nullable = false)
    private String repoType;
    @Column(name = "mono_repo", nullable = false)
    private boolean monoRepo;
    @Column(name = "branch_count")
    private int branchCount;
    @Column(name = "active_branch_count")
    private int activeBranchCount;
    @Column(name = "non_compliant_branch_count")
    private int nonCompliantBranchCount;
    @Column(name = "mr_count")
    private int mrCount;
    @Column(name = "open_mr_count")
    private int openMrCount;
    @Column(name = "merged_mr_count")
    private int mergedMrCount;
    @Column(name = "closed_mr_count")
    private int closedMrCount;
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    private long version;
    
    // 版本管理字段
    @Column(name = "initial_version", length = 50)
    private String initialVersion;
    @Column(name = "version_source", length = 20)
    private String versionSource;
}
