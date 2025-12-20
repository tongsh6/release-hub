package io.releasehub.infrastructure.persistence.iteration;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class IterationPersistenceAdapter implements IterationPort {
    private final Map<IterationKey, Iteration> store = new ConcurrentHashMap<>();

    @Override
    public void save(Iteration iteration) {
        store.put(iteration.getId(), iteration);
    }

    @Override
    public Optional<Iteration> findByKey(IterationKey key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public List<Iteration> findAll() {
        return new ArrayList<>(store.values());
    }
}
