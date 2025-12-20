package io.releasehub.application.iteration;

import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IterationAppService {
    private final IterationPort iterationPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Iteration create(String iterationKey, String description, Set<String> repoIds) {
        Set<RepoId> repos = repoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        Iteration it = Iteration.create(new IterationKey(iterationKey), description, repos, Instant.now(clock));
        iterationPort.save(it);
        return it;
    }
}
