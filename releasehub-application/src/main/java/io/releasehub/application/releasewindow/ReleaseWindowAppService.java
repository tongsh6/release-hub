package io.releasehub.application.releasewindow;

import io.releasehub.common.exception.BizException;
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
    public ReleaseWindow create(String name) {
        ReleaseWindow rw = ReleaseWindow.createDraft(name, Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    public ReleaseWindow get(String id) {
        return releaseWindowPort.findById(new ReleaseWindowId(id))
                .orElseThrow(() -> new BizException("RW_NOT_FOUND", "ReleaseWindow not found: " + id));
    }

    public List<ReleaseWindow> list() {
        return releaseWindowPort.findAll();
    }

    @Transactional
    public ReleaseWindow submit(String id) {
        ReleaseWindow rw = get(id);
        rw.submit(Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    @Transactional
    public ReleaseWindow configureWindow(String id, Instant startAt, Instant endAt) {
        ReleaseWindow rw = get(id);
        rw.configureWindow(startAt, endAt, Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    @Transactional
    public ReleaseWindow freeze(String id) {
        ReleaseWindow rw = get(id);
        rw.freeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    @Transactional
    public ReleaseWindow unfreeze(String id) {
        ReleaseWindow rw = get(id);
        rw.unfreeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    @Transactional
    public ReleaseWindow release(String id) {
        ReleaseWindow rw = get(id);
        rw.release(Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }

    @Transactional
    public ReleaseWindow close(String id) {
        ReleaseWindow rw = get(id);
        rw.close(Instant.now(clock));
        releaseWindowPort.save(rw);
        return rw;
    }
}
