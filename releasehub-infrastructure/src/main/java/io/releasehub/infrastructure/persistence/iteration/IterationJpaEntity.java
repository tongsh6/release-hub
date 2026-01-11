package io.releasehub.infrastructure.persistence.iteration;

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
@Table(name = "iteration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IterationJpaEntity {
    @Id
    @Column(name = "iteration_key")
    private String key;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
