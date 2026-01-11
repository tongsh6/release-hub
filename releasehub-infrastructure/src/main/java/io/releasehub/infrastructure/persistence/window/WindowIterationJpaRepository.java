package io.releasehub.infrastructure.persistence.window;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WindowIterationJpaRepository extends JpaRepository<WindowIterationJpaEntity, String> {
    List<WindowIterationJpaEntity> findByWindowId(String windowId);
    Optional<WindowIterationJpaEntity> findByWindowIdAndIterationKey(String windowId, String iterationKey);
    void deleteByWindowIdAndIterationKey(String windowId, String iterationKey);
}

