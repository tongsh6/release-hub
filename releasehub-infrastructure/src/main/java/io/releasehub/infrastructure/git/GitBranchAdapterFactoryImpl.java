package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.repo.GitProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitBranchAdapterFactoryImpl implements GitBranchAdapterFactory {

    private final List<GitBranchPort> adapters;

    public GitBranchAdapterFactoryImpl(List<GitBranchPort> adapters) {
        this.adapters = adapters;
    }

    @Override
    public GitBranchPort getAdapter(GitProvider provider) {
        GitProvider effectiveProvider = provider == null ? GitProvider.MOCK : provider;
        return adapters.stream()
                .filter(adapter -> adapter.supports(effectiveProvider))
                .findFirst()
                .orElseThrow(() -> ValidationException.invalidParameter("gitProvider"));
    }
}
