package io.releasehub.infrastructure.persistence.iteration;

import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.version.VersionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class IterationRepoPersistenceAdapter implements IterationRepoPort {
    
    private final IterationRepoJpaRepository jpaRepository;
    
    @Override
    public void saveWithVersion(String iterationKey, String repoId, String baseVersion,
                                 String devVersion, String targetVersion, String featureBranch,
                                 String versionSource, Instant versionSyncedAt,
                                 BranchCreationMode branchCreationMode) {
        IterationRepoId id = new IterationRepoId(iterationKey, repoId);
        Optional<IterationRepoJpaEntity> existing = jpaRepository.findById(id);
        String branchMode = branchCreationMode != null ? branchCreationMode.name() : BranchCreationMode.AUTO.name();
        
        IterationRepoJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setBaseVersion(baseVersion);
            entity.setDevVersion(devVersion);
            entity.setTargetVersion(targetVersion);
            entity.setFeatureBranch(featureBranch);
            entity.setVersionSource(versionSource);
            entity.setVersionSyncedAt(versionSyncedAt);
            entity.setBranchCreationMode(branchMode);
        } else {
            entity = new IterationRepoJpaEntity(id, baseVersion, devVersion, targetVersion,
                    featureBranch, versionSource, versionSyncedAt, branchMode);
        }
        
        jpaRepository.save(entity);
    }
    
    @Override
    public Optional<IterationRepoVersionInfo> getVersionInfo(String iterationKey, String repoId) {
        IterationRepoId id = new IterationRepoId(iterationKey, repoId);
        return jpaRepository.findById(id)
                .map(this::toVersionInfo);
    }
    
    @Override
    public List<IterationRepoVersionInfo> listVersionInfo(String iterationKey) {
        return jpaRepository.findByIdIterationKey(iterationKey).stream()
                .map(this::toVersionInfo)
                .collect(Collectors.toList());
    }
    
    @Override
    public void updateVersion(String iterationKey, String repoId, String devVersion,
                               String versionSource, Instant versionSyncedAt) {
        IterationRepoId id = new IterationRepoId(iterationKey, repoId);
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setDevVersion(devVersion);
            entity.setVersionSource(versionSource);
            entity.setVersionSyncedAt(versionSyncedAt);
            jpaRepository.save(entity);
        });
    }
    
    private IterationRepoVersionInfo toVersionInfo(IterationRepoJpaEntity e) {
        return IterationRepoVersionInfo.builder()
                .repoId(e.getId().getRepoId())
                .baseVersion(e.getBaseVersion())
                .devVersion(e.getDevVersion())
                .targetVersion(e.getTargetVersion())
                .featureBranch(e.getFeatureBranch())
                .branchCreationMode(parseBranchCreationMode(e.getBranchCreationMode()))
                .versionSource(e.getVersionSource() != null ? VersionSource.valueOf(e.getVersionSource()) : null)
                .versionSyncedAt(e.getVersionSyncedAt())
                .build();
    }

    private BranchCreationMode parseBranchCreationMode(String value) {
        if (value == null || value.isBlank()) {
            return BranchCreationMode.AUTO;
        }
        return BranchCreationMode.valueOf(value);
    }
}
