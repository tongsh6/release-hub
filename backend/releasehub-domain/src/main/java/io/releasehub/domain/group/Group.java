package io.releasehub.domain.group;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Group extends BaseEntity<GroupId> {
    private String name;
    private final String code;
    private String parentCode;

    public Group(GroupId id, String name, String code, String parentCode, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.code = code;
        this.parentCode = parentCode;
    }

    public static Group rehydrate(GroupId id, String name, String code, String parentCode, Instant createdAt, Instant updatedAt, long version) {
        return new Group(id, name, code, parentCode, createdAt, updatedAt, version);
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
            throw ValidationException.groupNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.groupNameTooLong(128);
        }
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw ValidationException.groupCodeRequired();
        }
        if (code.length() > 64) {
            throw ValidationException.groupCodeTooLong(64);
        }
    }

    private void validateParentCode(String parentCode, String selfCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return;
        }
        if (parentCode.length() > 64) {
            throw ValidationException.groupParentTooLong(64);
        }
        if (parentCode.equals(selfCode)) {
            throw BusinessException.groupParentSelf();
        }
    }

    public static Group create(String name, String code, String parentCode, Instant now) {
        return new Group(GroupId.of(code), name, code, parentCode, now);
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
