package io.releasehub.infrastructure.persistence.run;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunJpaRepository extends JpaRepository<RunJpaEntity, String> {
}
