package io.releasehub.domain.group;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Group extends BaseEntity<GroupId> {
    private String name;
    private final String code;
    private String parentCode;

    public Group(GroupId id, String name, String code, String parentCode, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.name = name;
        this.code = code;
        this.parentCode = parentCode;
    }

    private Group(GroupId id, String name, String code, String parentCode, Instant now) {
        super(id, now);
        validateName(name);
        validateCode(code);
        validateParentCode(parentCode, code);
        this.name = name;
        this.code = code;
        this.parentCode = parentCode;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("GROUP_NAME_REQUIRED", "Group name is required");
        }
        if (name.length() > 128) {
            throw new BizException("GROUP_NAME_TOO_LONG", "Group name is too long (max 128)");
        }
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("GROUP_CODE_REQUIRED", "Group code is required");
        }
        if (code.length() > 64) {
            throw new BizException("GROUP_CODE_TOO_LONG", "Group code is too long (max 64)");
        }
    }

    private void validateParentCode(String parentCode, String selfCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return;
        }
        if (parentCode.length() > 64) {
            throw new BizException("GROUP_PARENT_CODE_TOO_LONG", "Parent code is too long (max 64)");
        }
        if (parentCode.equals(selfCode)) {
            throw new BizException("GROUP_PARENT_SAME_AS_SELF", "Parent code cannot equal self code");
        }
    }

    public static Group create(String name, String code, String parentCode, Instant now) {
        return new Group(GroupId.newId(), name, code, parentCode, now);
    }

    public void rename(String name, Instant now) {
        validateName(name);
        this.name = name;
        touch(now);
    }

    public void changeParentCode(String parentCode, Instant now) {
        validateParentCode(parentCode, this.code);
        this.parentCode = parentCode;
        touch(now);
    }
}

