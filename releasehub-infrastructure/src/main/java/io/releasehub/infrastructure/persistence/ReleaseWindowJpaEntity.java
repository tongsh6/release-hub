package io.releasehub.infrastructure.persistence;

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
@Table(name = "release_window")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseWindowJpaEntity {
    @Id
    private String id;
    private String name;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "frozen", nullable = false)
    private boolean frozen;
}
