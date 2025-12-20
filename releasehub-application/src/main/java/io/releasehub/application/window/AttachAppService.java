package io.releasehub.application.window;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final IterationPort iterationPort;
    private final WindowIterationPort windowIterationPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public List<WindowIteration> attach(String windowId, List<String> iterationKeys) {
        releaseWindowPort.findById(new ReleaseWindowId(windowId)).orElseThrow();
        Instant now = Instant.now(clock);
        return iterationKeys.stream()
                .map(k -> new IterationKey(k))
                .peek(k -> iterationPort.findByKey(k).orElseThrow())
                .map(k -> windowIterationPort.attach(new ReleaseWindowId(windowId), k, now))
                .toList();
    }

    @Transactional
    public void detach(String windowId, String iterationKey) {
        releaseWindowPort.findById(new ReleaseWindowId(windowId)).orElseThrow();
        iterationPort.findByKey(new IterationKey(iterationKey)).orElseThrow();
        windowIterationPort.detach(new ReleaseWindowId(windowId), new IterationKey(iterationKey));
    }
}
