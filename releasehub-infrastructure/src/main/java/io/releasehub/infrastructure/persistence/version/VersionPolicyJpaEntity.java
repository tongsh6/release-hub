package io.releasehub.infrastructure.persistence.version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * VersionPolicy JPA 实体
 */
@Entity
@Table(name = "version_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VersionPolicyJpaEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String scheme;

    @Column(name = "bump_rule", nullable = false)
    private String bumpRule;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private long version;
}
