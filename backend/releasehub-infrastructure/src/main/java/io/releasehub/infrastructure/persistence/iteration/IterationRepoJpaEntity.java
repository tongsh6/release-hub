package io.releasehub.infrastructure.persistence.iteration;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "iteration_repo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IterationRepoJpaEntity {
    @EmbeddedId
    private IterationRepoId id;
    
    // 版本管理字段
    @Column(name = "base_version", length = 50)
    private String baseVersion;
    
    @Column(name = "dev_version", length = 50)
    private String devVersion;
    
    @Column(name = "target_version", length = 50)
    private String targetVersion;
    
    @Column(name = "feature_branch", length = 200)
    private String featureBranch;
    
    @Column(name = "version_source", length = 20)
    private String versionSource;
    
    @Column(name = "version_synced_at")
    private Instant versionSyncedAt;
    
    public IterationRepoJpaEntity(IterationRepoId id) {
        this.id = id;
    }
}

