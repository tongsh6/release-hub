package io.releasehub.application.group;

import io.releasehub.domain.group.Group;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GroupView {
    private String id;
    private String name;
    private String code;
    private String parentCode;
    private Instant createdAt;
    private Instant updatedAt;

    public static GroupView fromDomain(Group g) {
        return new GroupView(
                g.getId().value(),
                g.getName(),
                g.getCode(),
                g.getParentCode(),
                g.getCreatedAt(),
                g.getUpdatedAt()
        );
    }
}

