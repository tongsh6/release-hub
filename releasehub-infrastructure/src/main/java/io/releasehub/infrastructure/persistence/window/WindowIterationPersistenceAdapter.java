package io.releasehub.infrastructure.persistence.window;

import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WindowIterationPersistenceAdapter implements WindowIterationPort {

    private final WindowIterationJpaRepository jpaRepository;

    @Override
    public WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt) {
        String id = windowId.value() + "::" + iterationKey.value();
        Instant now = Instant.now();
        WindowIterationJpaEntity entity = jpaRepository.findByWindowIdAndIterationKey(windowId.value(), iterationKey.value())
                .map(e -> {
                    e.setAttachAt(attachAt);
                    e.setUpdatedAt(now);
                    return e;
                })
                .orElseGet(() -> new WindowIterationJpaEntity(
                        id,
                        windowId.value(),
                        iterationKey.value(),
                        attachAt,
                        now,
                        now
                ));
        jpaRepository.save(entity);
        return WindowIteration.rehydrate(
                id,
                new ReleaseWindowId(entity.getWindowId()),
                new IterationKey(entity.getIterationKey()),
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
                        e.getId(),
                        new ReleaseWindowId(e.getWindowId()),
                        new IterationKey(e.getIterationKey()),
                        e.getAttachAt(),
                        e.getCreatedAt(),
                        e.getUpdatedAt()
                ))
                .collect(java.util.stream.Collectors.toList());
    }
}
