package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGitBranchEvidenceTest {
    private static final String REPO = "https://fixture.invalid/team/repository.git";
    private static final String TOKEN = "local-fixture-only";
    private static final String FEATURE = "feature/iteration";
    private static final String RELEASE = "release/window";

    static Stream<Arguments> adapters() {
        var mock = new MockGitBranchAdapter();
        var inMemory = new InMemoryGitLabBranchAdapter();
        var factory = new GitBranchAdapterFactoryImpl(List.of(mock, inMemory));
        return Stream.of(
                Arguments.of("mock", factory.getAdapter(GitProvider.MOCK)),
                Arguments.of("in-memory-gitlab", factory.getAdapter(GitProvider.GITLAB)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void branchEvidenceContainsAnActualFixtureCommit(String name, GitBranchPort adapter) {
        assertTrue(adapter.createBranch(REPO, TOKEN, FEATURE, "main"));
        var branch = adapter.getBranchStatus(REPO, TOKEN, FEATURE);
        assertTrue(branch.exists());
        assertTrue(branch.latestCommit().matches("[0-9a-f]{40}|[0-9a-f]{64}"));
        assertEquals(adapter.getBranchStatus(REPO, TOKEN, "main").latestCommit(), branch.latestCommit());
        assertFalse(adapter.getBranchStatus(REPO, TOKEN, "missing").exists());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void mergeReturnsConsistentProjectMrAndCommitEvidence(String name, GitBranchPort adapter) {
        prepare(adapter, REPO);
        var evidence = merge(adapter, REPO);
        assertTrue(evidence.projectId().matches("[1-9][0-9]*"));
        assertTrue(evidence.iid().matches("[1-9][0-9]*"));
        assertTrue(URI.create(evidence.url()).getHost().endsWith(".invalid"));
        assertEquals(FEATURE, evidence.sourceBranch());
        assertEquals(RELEASE, evidence.targetBranch());
        assertEquals(adapter.getBranchStatus(REPO, TOKEN, FEATURE).latestCommit(), evidence.sourceSha());
        assertEquals(adapter.getBranchStatus(REPO, TOKEN, RELEASE).latestCommit(), evidence.mergeSha());
        assertEquals(GitBranchPort.MrDisposition.CREATED, evidence.disposition());

        var repeated = merge(adapter, REPO);
        assertEquals(evidence.iid(), repeated.iid());
        assertEquals(evidence.url(), repeated.url());
        assertEquals(evidence.projectId(), repeated.projectId());
        assertEquals(GitBranchPort.MrDisposition.ALREADY_MERGED, repeated.disposition());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void confirmedMergeCommitFlowsThroughTagAndPipeline(String name, GitBranchPort adapter) {
        prepare(adapter, REPO);
        var evidence = merge(adapter, REPO);
        var tag = adapter.createTag(REPO, TOKEN, evidence.projectId(), "v1.0.0", evidence.mergeSha(), "release");
        assertEquals(GitWriteResult.Outcome.APPLIED, tag.outcome());
        assertEquals(new GitBranchPort.TagEvidence(evidence.projectId(), "v1.0.0", evidence.mergeSha()), tag.evidence());
        var observed = adapter.readTag(REPO, TOKEN, evidence.projectId(), "v1.0.0");
        assertEquals(GitReadResult.Status.FOUND, observed.status());
        assertEquals(tag.evidence(), observed.evidence());
        assertEquals(GitWriteResult.Outcome.REJECTED,
                adapter.createTag(REPO, TOKEN, evidence.projectId(), "v1.0.0", evidence.mergeSha(), "repeat").outcome());

        var pipeline = adapter.triggerPipeline(REPO, TOKEN, evidence.projectId(), "v1.0.0", evidence.mergeSha());
        assertEquals(GitWriteResult.Outcome.APPLIED, pipeline.outcome());
        assertEquals(evidence.projectId(), pipeline.evidence().projectId());
        assertEquals("v1.0.0", pipeline.evidence().ref());
        assertEquals(evidence.mergeSha(), pipeline.evidence().sha());
        assertTrue(pipeline.evidence().id().matches("[1-9][0-9]*"));
        assertTrue(URI.create(pipeline.evidence().url()).getHost().endsWith(".invalid"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void missingSourceOrTargetCannotProduceMergeEvidence(String name, GitBranchPort adapter) {
        assertTrue(adapter.createBranch(REPO, TOKEN, RELEASE, "main"));
        var missingSource = adapter.mergeBranch(REPO, TOKEN, FEATURE, RELEASE, "merge");
        assertEquals(MergeStatus.FAILED, missingSource.status());
        assertNull(missingSource.mrEvidence());
        assertFalse(adapter.getBranchStatus(REPO, TOKEN, FEATURE).exists());

        assertTrue(adapter.createBranch(REPO, TOKEN, FEATURE, "main"));
        var missingTarget = adapter.mergeBranch(REPO, TOKEN, FEATURE, "release/missing", "merge");
        assertEquals(MergeStatus.FAILED, missingTarget.status());
        assertNull(missingTarget.mrEvidence());
        assertFalse(adapter.getBranchStatus(REPO, TOKEN, "release/missing").exists());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void projectIdentityIsOwnedByTheRepositoryInsteadOfTheCaller(String name, GitBranchPort adapter) {
        prepare(adapter, REPO);
        String otherRepo = "https://fixture.invalid/team/other.git";
        prepare(adapter, otherRepo);
        var first = merge(adapter, REPO);
        var other = merge(adapter, otherRepo);
        assertNotEquals(first.projectId(), other.projectId());
        assertEquals(GitReadResult.Status.MISMATCH,
                adapter.readTag(REPO, TOKEN, other.projectId(), "v1.0.0").status());
        assertEquals(GitWriteResult.Outcome.REJECTED,
                adapter.createTag(REPO, TOKEN, other.projectId(), "v1.0.0", first.mergeSha(), "release").outcome());
        assertEquals(GitReadResult.Status.NOT_FOUND,
                adapter.readTag(REPO, TOKEN, first.projectId(), "v1.0.0").status());
        assertEquals(GitWriteResult.Outcome.REJECTED,
                adapter.triggerPipeline(REPO, TOKEN, other.projectId(), RELEASE, first.mergeSha()).outcome());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void unavailableCommitCannotBeInventedByTagOrPipelineInputs(String name, GitBranchPort adapter) {
        prepare(adapter, REPO);
        var evidence = merge(adapter, REPO);
        String unknownSha = "0".repeat(40);
        assertNotEquals(unknownSha, evidence.mergeSha());
        for (String sha : List.of("not-a-sha", unknownSha)) {
            assertEquals(GitWriteResult.Outcome.REJECTED,
                    adapter.createTag(REPO, TOKEN, evidence.projectId(), "v1.0.0", sha, "release").outcome());
            assertEquals(GitWriteResult.Outcome.REJECTED,
                    adapter.triggerPipeline(REPO, TOKEN, evidence.projectId(), RELEASE, sha).outcome());
        }
        assertEquals(GitReadResult.Status.NOT_FOUND,
                adapter.readTag(REPO, TOKEN, evidence.projectId(), "v1.0.0").status());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void missingPipelineRefIsRejectedBeforeAnIdIsAllocated(String name, GitBranchPort adapter) {
        prepare(adapter, REPO);
        var evidence = merge(adapter, REPO);
        assertEquals(GitWriteResult.Outcome.REJECTED,
                adapter.triggerPipeline(REPO, TOKEN, evidence.projectId(), "missing", evidence.mergeSha()).outcome());
        var pipeline = adapter.triggerPipeline(REPO, TOKEN, evidence.projectId(), RELEASE, evidence.mergeSha());
        assertEquals(GitWriteResult.Outcome.APPLIED, pipeline.outcome());
        assertEquals("1", pipeline.evidence().id());
    }

    private void prepare(GitBranchPort adapter, String repo) {
        assertTrue(adapter.createBranch(repo, TOKEN, FEATURE, "main"));
        assertTrue(adapter.createBranch(repo, TOKEN, RELEASE, "main"));
    }

    private GitBranchPort.MrEvidence merge(GitBranchPort adapter, String repo) {
        var result = adapter.mergeBranch(repo, TOKEN, FEATURE, RELEASE, "merge");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertNull(result.remoteRequestId(), "A fixture MR IID is not a remote HTTP request ID");
        assertNotNull(result.mrEvidence(), "The actual local adapter must satisfy the consumer evidence contract");
        return result.mrEvidence();
    }
}
