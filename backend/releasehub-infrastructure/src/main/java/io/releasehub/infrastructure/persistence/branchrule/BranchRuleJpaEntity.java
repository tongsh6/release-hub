package io.releasehub.infrastructure.persistence.branchrule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * BranchRule JPA 实体
 */
@Entity
@Table(name = "branch_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchRuleJpaEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false)
    private String type;

    private String description;

    @ColumnDefault("'GLOBAL'")
    @Column(name = "scope_level", nullable = false, length = 32)
    private String scopeLevel;

    @Column(name = "scope_project_id", length = 128)
    private String scopeProjectId;

    @Column(name = "scope_sub_project_id", length = 128)
    private String scopeSubProjectId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private long version;
}
