package io.releasehub.application.run.plan;

import io.releasehub.domain.run.RunPlanSnapshot;
import java.time.Instant;

public record SavedRunPlan(String runId, String windowId, String operator, String idempotencyKey,
                           String requestHash, String planHash, Instant createdAt, RunPlanSnapshot snapshot) {}
