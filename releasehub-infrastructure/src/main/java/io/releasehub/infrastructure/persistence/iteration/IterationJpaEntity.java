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
import java.time.LocalDate;

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
    
    @Column(length = 500)
    private String name;

    @Column(name = "group_code")
    private String groupCode;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "expected_release_at")
    private LocalDate expectedReleaseAt;
    
    private Instant createdAt;
    private Instant updatedAt;
}
