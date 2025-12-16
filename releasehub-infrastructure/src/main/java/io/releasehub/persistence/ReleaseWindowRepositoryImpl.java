package io.releasehub.persistence;

import io.releasehub.releasewindow.ReleaseWindow;
import io.releasehub.releasewindow.ReleaseWindowId;
import io.releasehub.releasewindow.ReleaseWindowRepository;
import io.releasehub.releasewindow.ReleaseWindowStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReleaseWindowRepositoryImpl implements ReleaseWindowRepository {

    private final ReleaseWindowJpaRepository jpaRepository;

    @Override
    public void save(ReleaseWindow releaseWindow) {
        ReleaseWindowJpaEntity entity = new ReleaseWindowJpaEntity(
                releaseWindow.getId().value(),
                releaseWindow.getName(),
                releaseWindow.getStatus().name(),
                releaseWindow.getCreatedAt(),
                releaseWindow.getUpdatedAt(),
                releaseWindow.getStartAt(),
                releaseWindow.getEndAt(),
                releaseWindow.isFrozen()
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
                    new ReleaseWindowId(entity.getId()),
                    entity.getName(),
                    ReleaseWindowStatus.valueOf(entity.getStatus()),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
                    entity.getStartAt(),
                    entity.getEndAt(),
                    entity.isFrozen()
            );
        } catch (Exception e) {
            System.err.println("Error rehydrating entity: " + entity.getId());
            e.printStackTrace();
            throw e;
        }
    }
}
