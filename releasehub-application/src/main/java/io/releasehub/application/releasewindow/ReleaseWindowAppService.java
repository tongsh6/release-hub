package io.releasehub.application.releasewindow;

import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleaseWindowAppService {

    private final ReleaseWindowPort releaseWindowPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ReleaseWindowView create(String windowKey, String name) {
        ReleaseWindow rw = ReleaseWindow.createDraft(windowKey, name, Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    public ReleaseWindowView get(String id) {
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(id))
                .orElseThrow(() -> NotFoundException.releaseWindow(id));
        return ReleaseWindowView.from(rw);
    }

    public List<ReleaseWindowView> list() {
        return releaseWindowPort.findAll().stream()
                .map(ReleaseWindowView::from)
                .toList();
    }

    @Transactional
    public ReleaseWindowView publish(String id) {
        ReleaseWindow rw = findById(id);
        rw.publish(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView configureWindow(String id, Instant startAt, Instant endAt) {
        ReleaseWindow rw = findById(id);
        rw.configureWindow(startAt, endAt, Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView freeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.freeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView unfreeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.unfreeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView release(String id) {
        ReleaseWindow rw = findById(id);
        rw.release(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView close(String id) {
        ReleaseWindow rw = findById(id);
        rw.close(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    private ReleaseWindow findById(String id) {
         return releaseWindowPort.findById(new ReleaseWindowId(id))
                .orElseThrow(() -> NotFoundException.releaseWindow(id));
    }
}
