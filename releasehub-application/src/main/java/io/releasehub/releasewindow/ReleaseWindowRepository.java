package io.releasehub.releasewindow;

import java.util.List;
import java.util.Optional;

public interface ReleaseWindowRepository {
    void save(ReleaseWindow releaseWindow);
    Optional<ReleaseWindow> findById(ReleaseWindowId id);
    List<ReleaseWindow> findAll();
}
