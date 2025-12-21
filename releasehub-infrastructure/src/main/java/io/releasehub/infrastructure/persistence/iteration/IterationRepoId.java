package io.releasehub.infrastructure.persistence.iteration;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class IterationRepoId implements java.io.Serializable {
    private String iterationKey;
    private String repoId;
}

