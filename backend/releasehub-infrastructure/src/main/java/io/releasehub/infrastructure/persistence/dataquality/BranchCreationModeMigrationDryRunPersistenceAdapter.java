package io.releasehub.infrastructure.persistence.dataquality;

import io.releasehub.application.dataquality.BranchCreationModeMigrationDryRunPort;
import io.releasehub.infrastructure.persistence.iteration.IterationRepoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BranchCreationModeMigrationDryRunPersistenceAdapter implements BranchCreationModeMigrationDryRunPort {
    private final IterationRepoJpaRepository iterationRepoRepository;

    @Override
    public List<IterationRepoBranchModeRecord> listIterationRepoBranchModes() {
        return iterationRepoRepository.findAll().stream()
                .map(entity -> new IterationRepoBranchModeRecord(
                        entity.getId().getIterationKey(),
                        entity.getId().getRepoId(),
                        entity.getBranchCreationMode(),
                        entity.getFeatureBranch()))
                .toList();
    }
}
