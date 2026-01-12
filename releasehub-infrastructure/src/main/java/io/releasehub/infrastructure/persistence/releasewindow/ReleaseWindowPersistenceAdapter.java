package io.releasehub.infrastructure.persistence.releasewindow;

import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter：基础设施层对 Port 的实现
 */
@Repository
@RequiredArgsConstructor
public class ReleaseWindowPersistenceAdapter implements ReleaseWindowPort {

    private final ReleaseWindowJpaRepository jpaRepository;

    @Override
    public void save(ReleaseWindow releaseWindow) {
        ReleaseWindowJpaEntity entity = new ReleaseWindowJpaEntity(
                releaseWindow.getId().value(),
                releaseWindow.getWindowKey(),
                releaseWindow.getName(),
                releaseWindow.getStatus().name(),
                releaseWindow.getCreatedAt(),
                releaseWindow.getUpdatedAt(),
                releaseWindow.getStartAt(),
                releaseWindow.getEndAt(),
                releaseWindow.isFrozen(),
                releaseWindow.getPublishedAt()
        );
        jpaRepository.save(entity);
    }

    @Override
    public Optional<ReleaseWindow> findById(ReleaseWindowId id) {
        return jpaRepository.findById(id.value())
                            .map(this::toDomain);
    }

    @Override
    public List<ReleaseWindow> findAll() {
        return jpaRepository.findAll().stream()
                            .map(this::toDomain)
                            .collect(Collectors.toList());
    }

    private ReleaseWindow toDomain(ReleaseWindowJpaEntity entity) {
        try {
            return ReleaseWindow.rehydrate(
                    ReleaseWindowId.of(entity.getId()),
                    entity.getWindowKey(),
                    entity.getName(),
                    ReleaseWindowStatus.valueOf(entity.getStatus()),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
                    entity.getStartAt(),
                    entity.getEndAt(),
                    entity.isFrozen(),
                    entity.getPublishedAt()
            );
        } catch (Exception e) {
            System.err.println("Error rehydrating entity: " + entity.getId());
            e.printStackTrace();
            throw e;
        }
    }
}
