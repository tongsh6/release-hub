package io.releasehub.application.port.out;

import io.releasehub.domain.repo.GitProvider;

public interface GitBranchAdapterFactory {
    GitBranchPort getAdapter(GitProvider provider);
}
