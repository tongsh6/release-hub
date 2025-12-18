package io.releasehub.application.repo;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeRepositoryAppService {
    private final CodeRepositoryPort codeRepositoryPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CodeRepository create(String projectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo) {
        CodeRepository repo = CodeRepository.create(new ProjectId(projectId), name, cloneUrl, defaultBranch, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        return repo;
    }

    public CodeRepository get(String repoId) {
        return codeRepositoryPort.findById(new RepoId(repoId))
                .orElseThrow(() -> new BizException("REPO_NOT_FOUND", "Repository not found: " + repoId));
    }

    public List<CodeRepository> listByProject(String projectId) {
        return codeRepositoryPort.findByProjectId(new ProjectId(projectId));
    }
}
