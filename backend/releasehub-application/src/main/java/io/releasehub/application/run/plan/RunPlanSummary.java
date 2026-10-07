package io.releasehub.application.run.plan;

import java.time.Instant;

public record RunPlanSummary(String runId, String operator, Instant createdAt, int itemCount) {}
