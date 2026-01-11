package io.releasehub.infrastructure.persistence.run;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RunStepJpaEmbeddable {
    @Column(name = "action_type", nullable = false)
    private String actionType;
    @Column(name = "result", nullable = false)
    private String result;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    private String message;
}
