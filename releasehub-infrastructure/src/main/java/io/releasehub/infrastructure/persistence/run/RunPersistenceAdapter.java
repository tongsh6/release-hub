package io.releasehub.infrastructure.persistence.run;

import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.Run;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RunPersistenceAdapter implements RunPort {
    private final ConcurrentHashMap<String, Run> store = new ConcurrentHashMap<>();

    @Override
    public void save(Run run) {
        store.put(run.getId(), run);
    }

    @Override
    public Optional<Run> findById(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    @Override
    public List<Run> findAll() {
        return new ArrayList<>(store.values());
    }
}
