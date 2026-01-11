package io.releasehub.application.iteration;

import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;

import java.util.List;
import java.util.Optional;

public interface IterationPort {
    void save(Iteration iteration);
    Optional<Iteration> findByKey(IterationKey key);
    List<Iteration> findAll();
    void deleteByKey(IterationKey key);
}
