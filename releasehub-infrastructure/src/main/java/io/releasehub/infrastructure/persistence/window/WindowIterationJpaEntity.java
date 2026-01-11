package io.releasehub.infrastructure.persistence.window;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "window_iteration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WindowIterationJpaEntity {
    @Id
    private String id;
    @Column(name = "window_id", nullable = false)
    private String windowId;
    @Column(name = "iteration_key", nullable = false)
    private String iterationKey;
    @Column(name = "attach_at", nullable = false)
    private Instant attachAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

