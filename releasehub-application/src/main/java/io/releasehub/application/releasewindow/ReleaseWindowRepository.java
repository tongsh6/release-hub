package io.releasehub.application.releasewindow;

import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;

import java.util.List;
import java.util.Optional;

public interface ReleaseWindowRepository {
    void save(ReleaseWindow releaseWindow);
    Optional<ReleaseWindow> findById(ReleaseWindowId id);
    List<ReleaseWindow> findAll();
}
