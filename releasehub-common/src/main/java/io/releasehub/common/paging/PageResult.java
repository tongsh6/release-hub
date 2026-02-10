package io.releasehub.common.paging;

import java.util.List;

public record PageResult<T>(List<T> items, long total) {
}
