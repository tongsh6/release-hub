package io.releasehub.common.paging;

/**
 * 分页元数据 — 不可变 record，避免 EI_EXPOSE_REP。
 */
public record PageMeta(int page, int size, long total, int totalPages, boolean hasNext, boolean hasPrevious) {

    public PageMeta(int page, int size, long total) {
        this(
            Math.max(page, 1),
            size,
            total,
            (int) Math.ceil((double) total / Math.max(size, 1)),
            Math.max(page, 1) < (int) Math.ceil((double) total / Math.max(size, 1)),
            Math.max(page, 1) > 1
        );
    }
}
