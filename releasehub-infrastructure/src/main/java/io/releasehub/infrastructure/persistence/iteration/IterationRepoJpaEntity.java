package io.releasehub.infrastructure.persistence.iteration;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "iteration_repo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IterationRepoJpaEntity {
    @EmbeddedId
    private IterationRepoId id;
}

