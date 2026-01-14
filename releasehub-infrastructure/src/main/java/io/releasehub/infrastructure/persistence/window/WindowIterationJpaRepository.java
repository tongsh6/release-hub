package io.releasehub.infrastructure.persistence.window;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WindowIterationJpaRepository extends JpaRepository<WindowIterationJpaEntity, String> {
    List<WindowIterationJpaEntity> findByWindowId(String windowId);
    Page<WindowIterationJpaEntity> findByWindowId(String windowId, Pageable pageable);
    Optional<WindowIterationJpaEntity> findByWindowIdAndIterationKey(String windowId, String iterationKey);
    void deleteByWindowIdAndIterationKey(String windowId, String iterationKey);
}
