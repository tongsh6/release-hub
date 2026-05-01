package io.releasehub.infrastructure.conflict;

import io.releasehub.application.conflict.ConflictDetectionPort;
import io.releasehub.domain.conflict.ConflictReport;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConflictDetectionPersistenceAdapter implements ConflictDetectionPort {

    private final ConcurrentMap<String, ConflictReport> store = new ConcurrentHashMap<>();

    @Override
    public void saveReport(String windowId, ConflictReport report) {
        store.put(windowId, report);
    }

    @Override
    public Optional<ConflictReport> getLatestReport(String windowId) {
        return Optional.ofNullable(store.get(windowId));
    }
}
