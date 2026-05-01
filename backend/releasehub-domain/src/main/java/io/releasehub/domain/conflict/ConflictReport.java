package io.releasehub.domain.conflict;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 冲突报告值对象 — 发布窗口的冲突扫描结果
 */
public class ConflictReport {
    private final String windowId;
    private final Instant checkedAt;
    private final List<ConflictItem> conflicts;

    private ConflictReport(String windowId, Instant checkedAt, List<ConflictItem> conflicts) {
        this.windowId = Objects.requireNonNull(windowId);
        this.checkedAt = Objects.requireNonNull(checkedAt);
        this.conflicts = Collections.unmodifiableList(Objects.requireNonNull(conflicts));
    }

    public static ConflictReport of(String windowId, List<ConflictItem> conflicts) {
        return new ConflictReport(windowId, Instant.now(), conflicts);
    }

    public static ConflictReport empty(String windowId) {
        return new ConflictReport(windowId, Instant.now(), List.of());
    }

    public String getWindowId() { return windowId; }
    public Instant getCheckedAt() { return checkedAt; }
    public List<ConflictItem> getConflicts() { return conflicts; }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }
    public int totalCount() { return conflicts.size(); }
}
