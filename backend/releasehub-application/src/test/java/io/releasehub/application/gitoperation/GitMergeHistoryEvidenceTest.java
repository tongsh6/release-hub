package io.releasehub.application.gitoperation;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static io.releasehub.application.gitoperation.GitMergeRequestPort.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitMergeHistoryEvidenceTest {
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String HISTORICAL_MERGE_SHA = "b".repeat(40);
    private final GitMergeRequestPort port = mock(GitMergeRequestPort.class);

    @BeforeEach void sourceStillContained() {
        when(port.readSource("source")).thenReturn(GitReadResult.found(new SourceRevision("7", SOURCE_SHA)));
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of()));
        when(port.contains("target", SOURCE_SHA)).thenReturn(GitReadResult.found(true));
        var historical = new MergeRequest("17", "https://gitlab.test/project/-/merge_requests/17",
                "7", "source", "target", SOURCE_SHA, HISTORICAL_MERGE_SHA, State.MERGED,
                Readiness.UNKNOWN, null, null);
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(List.of(historical)));
    }

    @Test void aTargetResetToSourceMustNotAuthorizeTheOldMergeCommitForTagging() {
        when(port.contains("target", HISTORICAL_MERGE_SHA)).thenReturn(GitReadResult.found(false));
        var result = workflow().merge("source", "target", "release");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(GitBranchPort.MrDisposition.ALREADY_MERGED, result.mrEvidence().disposition());
        assertNull(result.mrEvidence().mergeSha(), "The target contains source, but no longer contains the old merge commit");
        verify(port).contains("target", HISTORICAL_MERGE_SHA);
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @Test void aConfirmedHistoricalMergeCommitRemainsAvailableForTagging() {
        when(port.contains("target", HISTORICAL_MERGE_SHA)).thenReturn(GitReadResult.found(true));
        var result = workflow().merge("source", "target", "release");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(HISTORICAL_MERGE_SHA, result.mrEvidence().mergeSha());
        verify(port).contains("target", HISTORICAL_MERGE_SHA);
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @Test void anUnavailableContainmentReadMustNotConfirmHistoricalMergeEvidence() {
        when(port.contains("target", HISTORICAL_MERGE_SHA)).thenReturn(GitReadResult.unavailable());
        var result = workflow().merge("source", "target", "release");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    private GitMergeWorkflow workflow() { return new GitMergeWorkflow(port, 2, () -> {}); }
}
