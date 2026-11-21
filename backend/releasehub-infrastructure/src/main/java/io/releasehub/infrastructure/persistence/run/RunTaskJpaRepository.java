package io.releasehub.infrastructure.persistence.run;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RunTaskJpaRepository extends JpaRepository<RunTaskJpaEntity, String> {
    List<RunTaskJpaEntity> findByRunIdOrderByTaskOrderAsc(String runId);
    boolean existsByRunId(String runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RunTaskJpaEntity t WHERE t.id = :id")
    Optional<RunTaskJpaEntity> findForUpdate(@Param("id") String id);
}
