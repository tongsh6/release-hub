package io.releasehub.application.conflict;

import io.releasehub.domain.conflict.ConflictReport;
import java.util.Optional;

public interface ConflictDetectionPort {
    void saveReport(String windowId, ConflictReport report);
    Optional<ConflictReport> getLatestReport(String windowId);
}
