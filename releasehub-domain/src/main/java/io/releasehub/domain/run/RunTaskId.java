package io.releasehub.domain.run;

import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * RunTask 实体 ID
 */
public record RunTaskId(String value) implements EntityId {
    
    public static RunTaskId of(String value) {
        return new RunTaskId(value);
    }
    
    public static RunTaskId generate() {
        return new RunTaskId(UUID.randomUUID().toString());
    }
    
    @Override
    public String toString() {
        return value;
    }
}
