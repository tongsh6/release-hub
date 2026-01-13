package io.releasehub.infrastructure.persistence.window;

import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;
import io.releasehub.domain.window.WindowIterationId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class WindowIterationPersistenceAdapter implements WindowIterationPort {

    private final WindowIterationJpaRepository jpaRepository;

    @Override
    public WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt) {
        WindowIterationId id = WindowIterationId.generate(windowId, iterationKey);
        Instant now = Instant.now();
        WindowIterationJpaEntity entity = jpaRepository.findByWindowIdAndIterationKey(windowId.value(), iterationKey.value())
                .map(e -> {
                    e.setAttachAt(attachAt);
                    e.setUpdatedAt(now);
                    return e;
                })
                .orElseGet(() -> new WindowIterationJpaEntity(
                        id.value(),
                        windowId.value(),
                        iterationKey.value(),
                        attachAt,
                        now,
                        now
                ));
        jpaRepository.save(entity);
        return WindowIteration.rehydrate(
                WindowIterationId.of(entity.getId()),
                ReleaseWindowId.of(entity.getWindowId()),
                IterationKey.of(entity.getIterationKey()),
                entity.getAttachAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    @Override
    public void detach(ReleaseWindowId windowId, IterationKey iterationKey) {
        jpaRepository.deleteByWindowIdAndIterationKey(windowId.value(), iterationKey.value());
    }

    @Override
    public List<WindowIteration> listByWindow(ReleaseWindowId windowId) {
        return jpaRepository.findByWindowId(windowId.value()).stream()
                .map(e -> WindowIteration.rehydrate(
                        WindowIterationId.of(e.getId()),
                        ReleaseWindowId.of(e.getWindowId()),
                        IterationKey.of(e.getIterationKey()),
                        e.getAttachAt(),
                        e.getCreatedAt(),
                        e.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<WindowIteration> findByWindowIdAndIterationKey(ReleaseWindowId windowId, IterationKey iterationKey) {
        return jpaRepository.findByWindowIdAndIterationKey(windowId.value(), iterationKey.value())
                .map(e -> WindowIteration.rehydrate(
                        WindowIterationId.of(e.getId()),
                        ReleaseWindowId.of(e.getWindowId()),
                        IterationKey.of(e.getIterationKey()),
                        e.getAttachAt(),
                        e.getCreatedAt(),
                        e.getUpdatedAt()
                ));
    }

    @Override
    public void updateReleaseBranch(String windowId, String iterationKey, String releaseBranch, Instant now) {
        jpaRepository.findByWindowIdAndIterationKey(windowId, iterationKey)
                .ifPresent(e -> {
                    e.setReleaseBranch(releaseBranch);
                    e.setBranchCreated(true);
                    e.setUpdatedAt(now);
                    jpaRepository.save(e);
                });
    }

    @Override
    public void updateLastMergeAt(String windowId, String iterationKey, Instant lastMergeAt) {
        jpaRepository.findByWindowIdAndIterationKey(windowId, iterationKey)
                .ifPresent(e -> {
                    e.setLastMergeAt(lastMergeAt);
                    e.setUpdatedAt(lastMergeAt);
                    jpaRepository.save(e);
                });
    }

    @Override
    public String getReleaseBranch(String windowId, String iterationKey) {
        return jpaRepository.findByWindowIdAndIterationKey(windowId, iterationKey)
                .map(WindowIterationJpaEntity::getReleaseBranch)
                .orElse(null);
    }
}
