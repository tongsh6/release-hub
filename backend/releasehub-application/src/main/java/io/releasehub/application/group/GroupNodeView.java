package io.releasehub.application.group;

import io.releasehub.domain.group.Group;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GroupNodeView {
    private String id;
    private String name;
    private String code;
    private String parentCode;
    private Instant createdAt;
    private Instant updatedAt;
    private List<GroupNodeView> children;

    public static GroupNodeView fromDomain(Group g) {
        return new GroupNodeView(
                g.getId().value(),
                g.getName(),
                g.getCode(),
                g.getParentCode(),
                g.getCreatedAt(),
                g.getUpdatedAt(),
                List.of()
        );
    }
}

