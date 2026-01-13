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
    
    // Release 分支管理字段
    @Column(name = "release_branch", length = 200)
    private String releaseBranch;
    @Column(name = "branch_created")
    private Boolean branchCreated;
    @Column(name = "last_merge_at")
    private Instant lastMergeAt;
    
    public WindowIterationJpaEntity(String id, String windowId, String iterationKey, 
                                     Instant attachAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.windowId = windowId;
        this.iterationKey = iterationKey;
        this.attachAt = attachAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.branchCreated = false;
    }
}

