package io.releasehub.infrastructure.persistence.releasewindow;

import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
                releaseWindow.getDescription(),
                releaseWindow.getPlannedReleaseAt(),
                releaseWindow.getStatus().name(),
                releaseWindow.getCreatedAt(),
                releaseWindow.getUpdatedAt(),
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

    @Override
    public PageResult<ReleaseWindow> findPaged(String name, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<ReleaseWindowJpaEntity> result;
        if (name == null || name.isBlank()) {
            result = jpaRepository.findAll(pageable);
        } else {
            result = jpaRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
        }
        List<ReleaseWindow> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    private ReleaseWindow toDomain(ReleaseWindowJpaEntity entity) {
        try {
            return ReleaseWindow.rehydrate(
                    ReleaseWindowId.of(entity.getId()),
                    entity.getWindowKey(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getPlannedReleaseAt(),
                    ReleaseWindowStatus.valueOf(entity.getStatus()),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
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
