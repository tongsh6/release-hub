package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author tongshuanglong
 */
@Getter
@NoArgsConstructor
public class Run extends BaseEntity<String> {
    private RunType runType;
    private String operator;
    private Instant startedAt;
    private Instant finishedAt;
    private List<RunItem> items = new ArrayList<>();

    // For Lombok Builder or manual construction
    @Builder
    private Run(String id, RunType runType, String operator, Instant startedAt, Instant finishedAt, List<RunItem> items, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt != null ? createdAt : startedAt, updatedAt != null ? updatedAt : startedAt, version);
        this.runType = runType;
        this.operator = operator;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        if (items != null) {
            this.items = new ArrayList<>(items);
        }
    }

    public static Run rehydrate(String id, RunType runType, String operator, Instant startedAt, Instant finishedAt, List<RunItem> items, Instant createdAt, Instant updatedAt, long version) {
        return new Run(id, runType, operator, startedAt, finishedAt, items, createdAt, updatedAt, version);
    }

    public static RunBuilder builder() {
        return new RunBuilder();
    }

    public static Run start(RunType runType, String operator, Instant now) {
        String id = runType.name() + "::" + now.toEpochMilli();
        return Run.builder()
                  .id(id)
                  .runType(runType)
                  .operator(operator)
                  .startedAt(now)
                  .createdAt(now)
                  .updatedAt(now)
                  .build();
    }

    public void finish(Instant now) {
        this.finishedAt = now;
        touch(now);
    }

    public List<RunItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(RunItem item) {
        items.add(item);
    }
}
