package io.releasehub.application.releasewindow;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;

import java.util.List;
import java.util.Optional;

/**
 * Port/Gateway：用例层对外部能力的抽象
 */
public interface ReleaseWindowPort {
    void save(ReleaseWindow releaseWindow);

    Optional<ReleaseWindow> findById(ReleaseWindowId id);

    List<ReleaseWindow> findAll();

    PageResult<ReleaseWindow> findPaged(String name, ReleaseWindowStatus status, int page, int size);
}
