package io.releasehub.application.branchrule;

import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchGovernanceAppService 测试")
class BranchGovernanceAppServiceTest {

    @Mock
    private CodeRepositoryPort codeRepositoryPort;
    @Mock
    private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock
    private GitBranchPort gitBranchPort;
    @Mock
    private BranchRuleUseCase branchRuleUseCase;

    private BranchGovernanceAppService appService;

    @BeforeEach
    void setUp() {
        appService = new BranchGovernanceAppService(codeRepositoryPort, gitBranchAdapterFactory, branchRuleUseCase);
    }

    @Test
    @DisplayName("列出活跃不合规历史分支并排除归档和基础分支")
    void shouldListActiveNonCompliantBranchesOnly() {
        CodeRepository repo = repo();
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(GitProvider.GITLAB)).thenReturn(gitBranchPort);
        when(gitBranchPort.listBranches(repo.getCloneUrl(), repo.getGitAccessToken(), "")).thenReturn(List.of(
                "main",
                "develop",
                "feature/ITER-1",
                "legacy_branch",
                "archive/unpublished/legacy_branch"
        ));
        when(branchRuleUseCase.isCompliant("feature/ITER-1", "G001", "repo-1")).thenReturn(true);
        when(branchRuleUseCase.isCompliant("legacy_branch", "G001", "repo-1")).thenReturn(false);

        List<BranchGovernanceAppService.NonCompliantBranch> result =
                appService.listNonCompliantBranches("repo-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).repositoryId()).isEqualTo("repo-1");
        assertThat(result.get(0).repositoryName()).isEqualTo("Payment Service");
        assertThat(result.get(0).branchName()).isEqualTo("legacy_branch");
        assertThat(result.get(0).scopeProjectId()).isEqualTo("G001");
        assertThat(result.get(0).scopeSubProjectId()).isEqualTo("repo-1");
        assertThat(result.get(0).actionBoundary()).isEqualTo("MANUAL_REVIEW_ONLY");

        verify(branchRuleUseCase, never()).isCompliant("main", "G001", "repo-1");
        verify(branchRuleUseCase, never()).isCompliant("develop", "G001", "repo-1");
        verify(branchRuleUseCase, never()).isCompliant("archive/unpublished/legacy_branch", "G001", "repo-1");
    }

    @Test
    @DisplayName("归档分支不会进入历史不合规治理清单")
    void shouldIgnoreArchiveBranches() {
        CodeRepository repo = repo();
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(GitProvider.GITLAB)).thenReturn(gitBranchPort);
        when(gitBranchPort.listBranches(repo.getCloneUrl(), repo.getGitAccessToken(), "")).thenReturn(List.of(
                "archive/unpublished/release-RW-1"
        ));

        List<BranchGovernanceAppService.NonCompliantBranch> result =
                appService.listNonCompliantBranches("repo-1");

        assertThat(result).isEmpty();
        verify(branchRuleUseCase, never()).isCompliant("archive/unpublished/release-RW-1", "G001", "repo-1");
    }

    private CodeRepository repo() {
        Instant now = Instant.parse("2026-05-23T00:00:00Z");
        return CodeRepository.rehydrate(
                RepoId.of("repo-1"),
                "Payment Service",
                "git@gitlab.example.com:release/payment-service.git",
                "main",
                "G001",
                RepoType.SERVICE,
                GitProvider.GITLAB,
                "repo-token",
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                now,
                now,
                0L
        );
    }
}
