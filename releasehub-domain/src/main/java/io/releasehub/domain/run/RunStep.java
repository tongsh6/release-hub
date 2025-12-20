package io.releasehub.domain.run;

import java.time.Instant;

public record RunStep(ActionType actionType, RunItemResult result, Instant startAt, Instant endAt, String message) {}
