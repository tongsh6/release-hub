package io.releasehub.application.branchrule;

import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BranchGovernanceAppService {

    private static final String ACTION_BOUNDARY = "MANUAL_REVIEW_ONLY";
    private static final String GUIDANCE = "Review branch owner before rename, delete, or archive outside ReleaseHub";

    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final BranchRuleUseCase branchRuleUseCase;

    @Transactional(readOnly = true)
    public List<NonCompliantBranch> listNonCompliantBranches(String repositoryId) {
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repositoryId))
                .orElseThrow(() -> NotFoundException.repository(repositoryId));
        var adapter = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        return adapter.listBranches(repo.getCloneUrl(), repo.getGitAccessToken(), "").stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .filter(name -> shouldReview(name, repo.getDefaultBranch()))
                .filter(name -> !branchRuleUseCase.isCompliant(name, repo.getGroupCode(), repo.getId().value()))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(name -> new NonCompliantBranch(
                        repo.getId().value(),
                        repo.getName(),
                        name,
                        repo.getGroupCode(),
                        repo.getId().value(),
                        ACTION_BOUNDARY,
                        GUIDANCE
                ))
                .toList();
    }

    private boolean shouldReview(String branchName, String defaultBranch) {
        if (branchName.startsWith("archive/")) {
            return false;
        }
        if (branchName.equals(defaultBranch)) {
            return false;
        }
        return !"main".equals(branchName) && !"master".equals(branchName) && !"develop".equals(branchName);
    }

    public record NonCompliantBranch(
            String repositoryId,
            String repositoryName,
            String branchName,
            String scopeProjectId,
            String scopeSubProjectId,
            String actionBoundary,
            String guidance
    ) {
    }
}
