package io.releasehub.infrastructure.persistence.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunTaskJpaRepository extends JpaRepository<RunTaskJpaEntity, String> {
    
    List<RunTaskJpaEntity> findByRunIdOrderByTaskOrder(String runId);
    
    List<RunTaskJpaEntity> findByRunIdAndStatus(String runId, String status);
}
