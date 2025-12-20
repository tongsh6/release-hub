package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.gitlab.GitLabPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GitLabAdapter implements GitLabPort {
    @Override
    public boolean branchExists(long projectId, String ref) {
        return true;
    }

    @Override
    public Optional<MrInfo> ensureMrInfo(long projectId, String source, String target) {
        return Optional.of(new MrInfo(false, false, null, null));
    }

    @Override
    public GateSummary fetchGateSummary(long projectId) {
        return new GateSummary(false, false, false, false);
    }
}
