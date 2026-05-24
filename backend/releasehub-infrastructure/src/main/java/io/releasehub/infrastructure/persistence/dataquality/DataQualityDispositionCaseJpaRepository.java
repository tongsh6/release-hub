package io.releasehub.infrastructure.persistence.dataquality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataQualityDispositionCaseJpaRepository extends JpaRepository<DataQualityDispositionCaseJpaEntity, String> {
    Optional<DataQualityDispositionCaseJpaEntity> findByCaseKey(String caseKey);

    List<DataQualityDispositionCaseJpaEntity> findAllByOrderByCreatedAtDesc();
}
