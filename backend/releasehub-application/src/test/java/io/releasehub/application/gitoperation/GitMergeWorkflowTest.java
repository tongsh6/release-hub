package io.releasehub.application.gitoperation;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import io.releasehub.application.port.out.GitBranchPort.MrDisposition;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static io.releasehub.application.gitoperation.GitMergeRequestPort.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitMergeWorkflowTest {
    private static final String SHA = "a".repeat(40);
    private static final String MERGE_SHA = "b".repeat(40);
    private final GitMergeRequestPort port = mock(GitMergeRequestPort.class);
    private final MergeRequest ready = mr("17", SHA, State.OPENED, Readiness.MERGEABLE, null);
    private final MergeRequest merged = mr("17", SHA, State.MERGED, Readiness.UNKNOWN, null);

    @BeforeEach void sourceIsNotYetIntegrated() {
        when(port.readSource("source")).thenReturn(GitReadResult.found(new SourceRevision("7", SHA)));
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of()));
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(false));
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"http-request-42"})
    void shouldKeepHttpRequestIdIndependentOfMrNumber(String requestId) {
        var evidence = new GitBranchPort.MrEvidence("7", "source", "target", "17", ready.url(), SHA, null,
                MrDisposition.MR_REUSED, null);
        var result = new GitBranchPort.MergeResult(MergeStatus.UNKNOWN, "unconfirmed", requestId, null).withEvidence(evidence);
        assertEquals(requestId, result.remoteRequestId());
        assertEquals("17", result.mrEvidence().iid());
    }

    @Test void shouldKeepConflictedMergeRequestOpenForHumanResolution() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, MergeBlockReason.CONFLICT)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.CONFLICT, result.status());
        assertEquals(MergeBlockReason.CONFLICT, result.mrEvidence().blockReason());
        assertEquals("17", result.mrEvidence().iid());
        verify(port, never()).merge(anyString(), any(), anyString());
        verify(port, times(1)).create("source", "target", "title");
    }

    @Test void shouldRequireEvidenceBeforeReportingNoCommitsAsAlreadyMerged() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.rejected("NO_COMMITS"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        verify(port, times(1)).create("source", "target", "title");
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldKeepUnknownCreateWithoutSendingAnotherWrite() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.unknown("RESPONSE_LOST"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("7", result.mrEvidence().projectId());
        assertEquals(SHA, result.mrEvidence().sourceSha());
        verify(port, times(1)).create("source", "target", "title");
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldNotRetryAfterUnknownMerge() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.unknown("RESPONSE_LOST"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("17", result.mrEvidence().iid());
        assertEquals("https://gitlab.test/project/-/merge_requests/17", result.mrEvidence().url());
        assertEquals(MrDisposition.CREATED, result.mrEvidence().disposition());
        verify(port, times(1)).merge("17", SHA, "title");
        verify(port, times(1)).create("source", "target", "title");
    }

    @Test void shouldNotSilentlyRetryRejectedMerge() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.rejected("HTTP_405"));
        assertEquals(MergeStatus.BLOCKED, workflow().merge("source", "target", "title").status());
        verify(port, times(1)).merge("17", SHA, "title");
    }

    @Test void shouldReuseExactOpenedMergeRequestWithoutCreatingAnother() {
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(ready)));
        when(port.read("17")).thenReturn(GitReadResult.found(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(MrDisposition.MR_REUSED, result.mrEvidence().disposition());
        assertEquals(MERGE_SHA, result.mrEvidence().mergeSha());
        verify(port, never()).create(anyString(), anyString(), anyString());
        verify(port).merge("17", SHA, "title");
    }

    @Test void shouldRequireCurrentContainmentToReuseMergedHistory() {
        var historical = mr("12", SHA, State.MERGED, Readiness.UNKNOWN, null);
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(true));
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(List.of(historical)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(MrDisposition.ALREADY_MERGED, result.mrEvidence().disposition());
        assertEquals("12", result.mrEvidence().iid());
        verifyNoWrites();
    }

    @Test void shouldNotAcceptOldMergedHistoryAfterTargetWasReset() {
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(
                List.of(mr("12", SHA, State.MERGED, Readiness.UNKNOWN, null))));
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MrDisposition.CREATED, result.mrEvidence().disposition());
        assertEquals("17", result.mrEvidence().iid());
        verify(port).contains("target", SHA);
        verify(port).create("source", "target", "title");
    }

    @Test void shouldNotAttachOldMergedMrToANewerSourceRevision() {
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(true));
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(
                List.of(mr("12", "c".repeat(40), State.MERGED, Readiness.UNKNOWN, null))));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertNull(result.mrEvidence().iid());
        assertEquals(SHA, result.mrEvidence().sourceSha());
        verifyNoWrites();
    }

    @Test void shouldNotAttributeAnAlreadyContainedRevisionToAStaleOpenedMr() {
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(true));
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(
                List.of(mr("12", "c".repeat(40), State.OPENED, Readiness.MERGEABLE, null))));
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(List.of()));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertNull(result.mrEvidence().iid());
        assertEquals(SHA, result.mrEvidence().sourceSha());
        verifyNoWrites();
    }

    @Test void shouldRetainKnownIdFromAnIncompleteCreateResponseWithoutMerging() {
        var partial = new MergeRequest("17", null, null, null, null, null, null,
                State.UNKNOWN, Readiness.UNKNOWN, MergeBlockReason.UNKNOWN, null);
        when(port.create("source", "target", "title")).thenReturn(
                new GitWriteResult<>(GitWriteResult.Outcome.UNKNOWN, partial, "INCOMPLETE_RESPONSE"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("17", result.mrEvidence().iid());
        assertEquals(SHA, result.mrEvidence().sourceSha());
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldSkipEmptyMergeRequestWhenFreshBranchesAlreadyContainSameCommit() {
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(true));
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.found(List.of()));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MrDisposition.ALREADY_MERGED, result.mrEvidence().disposition());
        assertEquals("source", result.mrEvidence().sourceBranch());
        assertEquals("target", result.mrEvidence().targetBranch());
        verifyNoWrites();
    }

    @Test void shouldStopWhenLookupIsIncompleteInsteadOfRecreating() {
        when(port.find("source", "target", State.OPENED)).thenReturn(new GitReadResult<>(GitReadResult.Status.INCOMPLETE, null));
        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verifyNoWrites();
    }

    @Test void shouldStopWhenContainmentCannotBeConfirmed() {
        when(port.contains("target", SHA)).thenReturn(GitReadResult.unavailable());
        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verifyNoWrites();
    }

    @Test void shouldStopWhenMultipleOpenedMergeRequestsMakeIdentityAmbiguous() {
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(
                List.of(ready, mr("18", SHA, State.OPENED, Readiness.MERGEABLE, null))));
        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verifyNoWrites();
    }

    @Test void shouldStopBeforeMergeWhenSourceChangedSinceInitialRead() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(
                mr("17", "c".repeat(40), State.OPENED, Readiness.MERGEABLE, null)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("SOURCE_REVISION_CHANGED", result.detail());
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldStopWhenReadFailsBeforeMerge() {
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(ready)));
        when(port.read("17")).thenReturn(new GitReadResult<>(GitReadResult.Status.FORBIDDEN, null));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.BLOCKED, result.status());
        assertEquals(MergeBlockReason.PERMISSION_DENIED, result.mrEvidence().blockReason());
        assertEquals("17", result.mrEvidence().iid());
        verifyNoWrites();
    }

    @ParameterizedTest @EnumSource(MergeBlockReason.class)
    void shouldPreserveEveryConfirmedGateAndItsEvidence(MergeBlockReason reason) {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, reason)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(reason == MergeBlockReason.CONFLICT ? MergeStatus.CONFLICT : MergeStatus.BLOCKED, result.status());
        assertEquals(reason, result.mrEvidence().blockReason());
        assertEquals("17", result.mrEvidence().iid());
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldNotCallAnIncompleteSuccessfulHttpResponseMerged() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verify(port, times(1)).merge("17", SHA, "title");
    }

    @Test void shouldOnlyPollReadinessWithoutRepeatingCreate() {
        when(port.create("source", "target", "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.PENDING, null)));
        when(port.read("17")).thenReturn(GitReadResult.found(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        assertEquals(MergeStatus.SUCCESS, workflow().merge("source", "target", "title").status());
        verify(port, times(1)).create("source", "target", "title");
        verify(port, times(1)).read("17");
    }

    @Test void shouldProbeWithoutCreatingOrClosingAnyRemoteResource() {
        assertFalse(workflow().probe("source", "target").canMerge());
        verifyNoWrites();
    }

    @Test void shouldProbeAnExistingMergeRequestWithReadOnlyCalls() {
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(ready)));
        when(port.read("17")).thenReturn(GitReadResult.found(ready));
        assertTrue(workflow().probe("source", "target").canMerge());
        verifyNoWrites();
    }

    private GitMergeWorkflow workflow() { return new GitMergeWorkflow(port, 2, () -> {}); }
    private void verifyNoWrites() {
        verify(port, never()).create(anyString(), anyString(), anyString());
        verify(port, never()).merge(anyString(), any(), anyString());
    }
    private static MergeRequest mr(String id, String sourceSha, State state, Readiness readiness, MergeBlockReason reason) {
        return new MergeRequest(id, "https://gitlab.test/project/-/merge_requests/" + id, "7", "source", "target",
                sourceSha, state == State.MERGED ? MERGE_SHA : null, state, readiness, reason, reason == null ? null : reason.name());
    }
}
