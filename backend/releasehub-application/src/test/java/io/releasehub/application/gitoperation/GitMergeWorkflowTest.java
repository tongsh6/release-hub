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
        when(port.contains("target", MERGE_SHA)).thenReturn(GitReadResult.found(true));
        when(port.read("17")).thenReturn(GitReadResult.found(ready));
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
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, MergeBlockReason.CONFLICT)));
        when(port.read("17")).thenReturn(GitReadResult.found(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, MergeBlockReason.CONFLICT)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.CONFLICT, result.status());
        assertEquals(MergeBlockReason.CONFLICT, result.mrEvidence().blockReason());
        assertEquals("17", result.mrEvidence().iid());
        verify(port, never()).merge(anyString(), any(), anyString());
        verify(port, times(1)).create("source", "target", SHA, "title");
    }

    @Test void shouldRequireEvidenceBeforeReportingNoCommitsAsAlreadyMerged() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.rejected("NO_COMMITS"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldKeepUnknownCreateWithoutSendingAnotherWrite() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.unknown("RESPONSE_LOST"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("7", result.mrEvidence().projectId());
        assertEquals(SHA, result.mrEvidence().sourceSha());
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldNotRetryAfterUnknownMerge() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.unknown("RESPONSE_LOST"));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("17", result.mrEvidence().iid());
        assertEquals("https://gitlab.test/project/-/merge_requests/17", result.mrEvidence().url());
        assertEquals(MrDisposition.CREATED, result.mrEvidence().disposition());
        verify(port, times(1)).merge("17", SHA, "title");
        verify(port, times(1)).create("source", "target", SHA, "title");
    }

    @Test void shouldNotSilentlyRetryRejectedMerge() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
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
        verify(port, never()).create(anyString(), anyString(), anyString(), anyString());
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
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        var result = workflow().merge("source", "target", "title");
        assertEquals(MrDisposition.CREATED, result.mrEvidence().disposition());
        assertEquals("17", result.mrEvidence().iid());
        verify(port, atLeastOnce()).contains("target", SHA);
        verify(port).create("source", "target", SHA, "title");
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
        when(port.create("source", "target", SHA, "title")).thenReturn(
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
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(
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
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, reason)));
        when(port.read("17")).thenReturn(GitReadResult.found(
                mr("17", SHA, State.OPENED, Readiness.BLOCKED, reason)));
        var result = workflow().merge("source", "target", "title");
        assertEquals(reason == MergeBlockReason.CONFLICT ? MergeStatus.CONFLICT : MergeStatus.BLOCKED, result.status());
        assertEquals(reason, result.mrEvidence().blockReason());
        assertEquals("17", result.mrEvidence().iid());
        verify(port, never()).merge(anyString(), any(), anyString());
    }

    @Test void shouldNotCallAnIncompleteSuccessfulHttpResponseMerged() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(ready));
        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verify(port, times(1)).merge("17", SHA, "title");
    }

    @Test void shouldOnlyPollReadinessWithoutRepeatingCreate() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.PENDING, null)));
        when(port.read("17")).thenReturn(GitReadResult.found(
                mr("17", SHA, State.OPENED, Readiness.PENDING, null)), GitReadResult.found(ready));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        assertEquals(MergeStatus.SUCCESS, workflow().merge("source", "target", "title").status());
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, times(2)).read("17");
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

    @ParameterizedTest @ValueSource(ints = {41, 48, 63})
    void shouldRejectNonCommitShaBeforeCreatingACheckpoint(int length) {
        String malformed = "a".repeat(length);
        when(port.readSource("source")).thenReturn(GitReadResult.found(new SourceRevision("7", malformed)));
        when(port.contains("target", malformed)).thenReturn(GitReadResult.found(false));
        when(port.create("source", "target", malformed, "title")).thenReturn(GitWriteResult.applied(
                mr("17", malformed, State.OPENED, Readiness.MERGEABLE, null)));
        when(port.merge("17", malformed, "title")).thenReturn(GitWriteResult.applied(
                mr("17", malformed, State.MERGED, Readiness.UNKNOWN, null)));

        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verifyNoWrites();
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "/relative/mr/17"})
    void shouldRequireAUsableUrlForAnMrCheckpoint(String url) {
        var incomplete = new MergeRequest("17", url, "7", "source", "target", SHA, null,
                State.OPENED, Readiness.MERGEABLE, null, null);
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(incomplete)));
        when(port.read("17")).thenReturn(GitReadResult.found(incomplete));
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));

        assertEquals(MergeStatus.UNKNOWN, workflow().merge("source", "target", "title").status());
        verifyNoWrites();
    }

    @Test void ensureMustReturnBeforeAnyMergeOrReadinessPolling() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(
                mr("17", SHA, State.OPENED, Readiness.PENDING, null)));
        var ensured = workflow().ensureMergeRequest("source", "target", "title", null);
        assertEquals(GitWriteResult.Outcome.APPLIED, ensured.outcome());
        assertEquals(MrDisposition.CREATED, ensured.evidence().disposition());
        assertTrue(GitMergeWorkflow.isValidCheckpoint(ensured.evidence()));
        verify(port).create("source", "target", SHA, "title");
        verify(port, never()).read(anyString());
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void createdCheckpointMustNotQueryOrAttributeAnUnrelatedMergeSha(boolean unrelatedCommitIsContained) {
        var created = new MergeRequest("17", ready.url(), "7", "source", "target", SHA, MERGE_SHA,
                State.OPENED, Readiness.PENDING, null, null);
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(created));
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(false), GitReadResult.found(true));
        when(port.contains("target", MERGE_SHA)).thenReturn(unrelatedCommitIsContained
                ? GitReadResult.found(true) : GitReadResult.unavailable());
        var workflow = workflow();

        var ensured = workflow.ensureMergeRequest("source", "target", "title", null);
        assertEquals(GitWriteResult.Outcome.APPLIED, ensured.outcome());
        var result = workflow.tryMergeRequest(ensured.evidence(), "title");

        assertAll(
                () -> assertNull(ensured.evidence().mergeSha(), "CREATE owns no merge result"),
                () -> assertEquals(MergeStatus.SUCCESS, result.status()),
                () -> assertEquals(MrDisposition.ALREADY_MERGED, result.mrEvidence().disposition()),
                () -> assertNull(result.mrEvidence().mergeSha(), "unrelated commit must not become this merge's result"),
                () -> verify(port, never()).contains("target", MERGE_SHA));
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, never()).read(anyString());
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(ints = {40, 64})
    void createdCheckpointPreservesResponseShaAfterEquivalentHexComparison(int length) {
        String frozen = "aBcD".repeat(length / 4);
        String observed = frozen.toLowerCase(java.util.Locale.ROOT);
        when(port.readSource("source")).thenReturn(GitReadResult.found(new SourceRevision("7", frozen)));
        when(port.contains("target", frozen)).thenReturn(GitReadResult.found(false));
        when(port.create("source", "target", frozen, "title")).thenReturn(GitWriteResult.applied(
                new MergeRequest("17", ready.url(), "7", "source", "target", observed, null,
                        State.OPENED, Readiness.PENDING, null, null)));

        var result = workflow().ensureMergeRequest("source", "target", "title", null);

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(MrDisposition.CREATED, result.evidence().disposition());
        assertEquals(observed, result.evidence().sourceSha());
        assertNull(result.evidence().mergeSha());
        verify(port, never()).read(anyString());
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @Test void tryMustUseThePersistedCheckpointWithoutReselectingOrCreating() {
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(merged));
        var result = workflow().tryMergeRequest(checkpoint(MrDisposition.CREATED, null), "title");
        assertEquals(MergeStatus.SUCCESS, result.status());
        verify(port).merge("17", SHA, "title");
        verify(port, never()).readSource(anyString());
        verify(port, never()).find(anyString(), anyString(), any());
        verify(port, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = MrDisposition.class, names = {"CREATED", "MR_REUSED"})
    void tryMergeAcceptsConfirmedFastForwardWithoutInventingAMergeCommit(MrDisposition disposition) {
        var confirmed = new MergeRequest("17", ready.url(), "7", "source", "target",
                SHA.toUpperCase(java.util.Locale.ROOT), null, State.MERGED, Readiness.UNKNOWN, null, null);
        when(port.merge("17", SHA, "title")).thenReturn(GitWriteResult.applied(confirmed));

        var result = workflow().tryMergeRequest(checkpoint(disposition, MERGE_SHA), "title");

        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(disposition, result.mrEvidence().disposition());
        assertNull(result.mrEvidence().mergeSha());
        assertEquals(confirmed.sourceSha(), result.mrEvidence().sourceSha());
        verify(port).merge("17", SHA, "title");
        verify(port, never()).contains("target", MERGE_SHA);
        verify(port, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @Test void tryMustRejectAProjectChangeEvenWhenMrIdAndBranchesMatch() {
        var foreign = new MergeRequest("17", ready.url(), "8", "source", "target", SHA, null,
                State.OPENED, Readiness.MERGEABLE, null, null);
        when(port.read("17")).thenReturn(GitReadResult.found(foreign));
        assertEquals(MergeStatus.UNKNOWN, workflow().tryMergeRequest(checkpoint(MrDisposition.MR_REUSED, null), "title").status());
        verifyNoWrites();
    }

    @Test void tryMustRejectSourceAdvanceAfterTheEnsureCheckpoint() {
        when(port.read("17")).thenReturn(GitReadResult.found(mr("17", "c".repeat(40), State.OPENED, Readiness.MERGEABLE, null)));
        assertEquals(MergeStatus.UNKNOWN, workflow().tryMergeRequest(checkpoint(MrDisposition.CREATED, null), "title").status());
        verifyNoWrites();
    }

    @Test void tryMustNotReplayAFormerlyContainedRevisionAfterTargetReset() {
        var checkpoint = new GitBranchPort.MrEvidence("7", "source", "target", null, null, SHA, null,
                MrDisposition.ALREADY_MERGED, null);
        assertEquals(MergeStatus.UNKNOWN, workflow().tryMergeRequest(checkpoint, "title").status());
        verifyNoWrites();
        verify(port, never()).read(anyString());
    }

    @Test void mergedMrStateAloneCannotCompleteATaskAfterTargetReset() {
        when(port.read("17")).thenReturn(GitReadResult.found(merged));
        assertEquals(MergeStatus.UNKNOWN, workflow().tryMergeRequest(checkpoint(MrDisposition.CREATED, null), "title").status());
        verifyNoWrites();
    }

    // B9 independent contract: a prepared CREATE_MR keeps its original project and revision.
    @ParameterizedTest @ValueSource(strings = {"MOVED", "MISSING"})
    void createMrIntentContainedRevisionNeedsNoSourceOrMrReads(String currentSource) {
        when(port.readSource("source")).thenReturn("MOVED".equals(currentSource)
                ? GitReadResult.found(new SourceRevision("7", "c".repeat(40)))
                : new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null));
        when(port.contains("target", SHA)).thenReturn(GitReadResult.found(true));
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.unavailable());
        when(port.find("source", "target", State.MERGED)).thenReturn(GitReadResult.unavailable());

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertCreateMrIntentIdentity(result.evidence(), SHA);
        assertAll(
                () -> assertEquals(MrDisposition.ALREADY_MERGED, result.evidence().disposition()),
                () -> assertNull(result.evidence().iid()),
                () -> assertNull(result.evidence().url()),
                () -> assertNull(result.evidence().mergeSha()),
                () -> assertNull(result.evidence().blockReason()));
        assertTrue(GitMergeWorkflow.isValidCheckpoint(result.evidence()));
        verify(port).contains("target", SHA);
        verifyNoMoreInteractions(port);
    }

    @ParameterizedTest @ValueSource(ints = {40, 64})
    void createMrIntentReusesOpenedOriginalRevisionWithoutReadingMovedSource(int length) {
        String frozen = "aBcD".repeat(length / 4);
        String observed = frozen.toLowerCase(java.util.Locale.ROOT);
        var existing = mr("17", observed, State.OPENED, Readiness.MERGEABLE, null);
        when(port.readSource("source")).thenReturn(GitReadResult.found(new SourceRevision("7", "c".repeat(length))));
        when(port.contains("target", frozen)).thenReturn(GitReadResult.found(false));
        when(port.contains("target", "c".repeat(length))).thenReturn(GitReadResult.found(false));
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(existing)));
        when(port.read("17")).thenReturn(GitReadResult.found(existing));

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", frozen));

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertCreateMrIntentIdentity(result.evidence(), frozen);
        assertAll(
                () -> assertEquals(MrDisposition.MR_REUSED, result.evidence().disposition()),
                () -> assertEquals("17", result.evidence().iid()),
                () -> assertEquals(existing.url(), result.evidence().url()),
                () -> assertNull(result.evidence().mergeSha()));
        assertTrue(GitMergeWorkflow.isValidCheckpoint(result.evidence()));
        verify(port).contains("target", frozen);
        verify(port).read("17");
        verify(port, never()).readSource(anyString());
        verifyNoWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"MOVED", "MISSING", "UNAVAILABLE", "FOREIGN_PROJECT"})
    void createMrIntentWithoutAnOpenedMrCannotReplaceTheOriginalSource(String currentSource) {
        GitReadResult<SourceRevision> sourceRead = switch (currentSource) {
            case "MOVED" -> GitReadResult.found(new SourceRevision("7", "c".repeat(40)));
            case "MISSING" -> new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null);
            case "UNAVAILABLE" -> GitReadResult.unavailable();
            case "FOREIGN_PROJECT" -> GitReadResult.found(new SourceRevision("8", SHA));
            default -> throw new AssertionError(currentSource);
        };
        when(port.readSource("source")).thenReturn(sourceRead);
        when(port.contains("target", "c".repeat(40))).thenReturn(GitReadResult.found(false));
        when(port.create(anyString(), anyString(), anyString(), anyString())).thenReturn(GitWriteResult.applied(ready));

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertCreateMrIntentUnsentFailure(result, GitWriteResult.Outcome.UNKNOWN);
        verify(port).contains("target", SHA);
        verify(port).find("source", "target", State.OPENED);
        verify(port).readSource("source");
        verify(port, never()).read(anyString());
    }

    @Test
    void createMrIntentCreatesOnceOnlyWhenTheLiveSourceStillMatchesTheOriginalRevision() {
        when(port.create("source", "target", SHA, "title")).thenReturn(GitWriteResult.applied(ready));

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertCreateMrIntentIdentity(result.evidence(), SHA);
        assertEquals(MrDisposition.CREATED, result.evidence().disposition());
        assertEquals("17", result.evidence().iid());
        assertTrue(GitMergeWorkflow.isValidCheckpoint(result.evidence()));
        verify(port).contains("target", SHA);
        verify(port).find("source", "target", State.OPENED);
        verify(port).readSource("source");
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, never()).read(anyString());
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"MULTIPLE", "INCOMPLETE", "FOREIGN_PROJECT"})
    void createMrIntentUnconfirmedOpenedListCannotBorrowAnMrOrAuthorizeCreation(String observation) {
        GitReadResult<List<MergeRequest>> opened = switch (observation) {
            case "MULTIPLE" -> GitReadResult.found(List.of(ready,
                    mr("18", SHA, State.OPENED, Readiness.MERGEABLE, null)));
            case "INCOMPLETE" -> new GitReadResult<>(GitReadResult.Status.INCOMPLETE, null);
            case "FOREIGN_PROJECT" -> GitReadResult.found(List.of(new MergeRequest("17", ready.url(), "8",
                    "source", "target", SHA, MERGE_SHA, State.OPENED, Readiness.MERGEABLE, null, null)));
            default -> throw new AssertionError(observation);
        };
        when(port.find("source", "target", State.OPENED)).thenReturn(opened);

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertCreateMrIntentUnsentFailure(result, GitWriteResult.Outcome.UNKNOWN);
        verify(port).contains("target", SHA);
        verify(port, never()).readSource(anyString());
        verify(port, never()).read(anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"SHA", "PROJECT", "CLOSED", "UNAVAILABLE", "FORBIDDEN"})
    void createMrIntentUnconfirmedDetailCannotPolluteTheOriginalCheckpoint(String observation) {
        when(port.find("source", "target", State.OPENED)).thenReturn(GitReadResult.found(List.of(ready)));
        GitReadResult<MergeRequest> detail = switch (observation) {
            case "SHA" -> GitReadResult.found(mr("17", "c".repeat(40), State.OPENED, Readiness.MERGEABLE, null));
            case "PROJECT" -> GitReadResult.found(new MergeRequest("17", ready.url(), "8", "source", "target",
                    SHA, MERGE_SHA, State.OPENED, Readiness.MERGEABLE, null, null));
            case "CLOSED" -> GitReadResult.found(mr("17", SHA, State.CLOSED, Readiness.UNKNOWN, null));
            case "UNAVAILABLE" -> GitReadResult.unavailable();
            case "FORBIDDEN" -> new GitReadResult<>(GitReadResult.Status.FORBIDDEN, null);
            default -> throw new AssertionError(observation);
        };
        when(port.read("17")).thenReturn(detail);

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertCreateMrIntentUnsentFailure(result, "FORBIDDEN".equals(observation)
                ? GitWriteResult.Outcome.REJECTED : GitWriteResult.Outcome.UNKNOWN);
        if ("FORBIDDEN".equals(observation)) {
            assertEquals(MergeBlockReason.PERMISSION_DENIED, result.evidence().blockReason());
        }
        verify(port).contains("target", SHA);
        verify(port).read("17");
        verify(port, never()).readSource(anyString());
    }

    @Test
    void createMrIntentResponseForAdvancedSourceRemainsUnknownWithoutAnotherWrite() {
        var observed = mr("17", "c".repeat(40), State.OPENED, Readiness.MERGEABLE, null);
        when(port.create("source", "target", SHA, "title")).thenReturn(
                new GitWriteResult<>(GitWriteResult.Outcome.UNKNOWN, observed, "INCOMPLETE_RESPONSE"));

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
        assertCreateMrIntentIdentity(result.evidence(), SHA);
        assertEquals("17", result.evidence().iid(), "a sent response may retain its known iid for diagnosis");
        assertNull(result.evidence().disposition());
        assertFalse(GitMergeWorkflow.isValidCheckpoint(result.evidence()));
        verify(port, times(1)).create("source", "target", SHA, "title");
        verify(port, never()).merge(anyString(), anyString(), anyString());
    }

    @Test
    void createMrIntentUnknownContainmentDoesNotReadUnrelatedEvidenceOrCreate() {
        when(port.contains("target", SHA)).thenReturn(GitReadResult.unavailable());

        var result = workflow().ensureMergeRequest("source", "target", "title", new SourceRevision("7", SHA));

        assertCreateMrIntentUnsentFailure(result, GitWriteResult.Outcome.UNKNOWN);
        verify(port).contains("target", SHA);
        verify(port, never()).readSource(anyString());
        verify(port, never()).find(anyString(), anyString(), any());
        verify(port, never()).read(anyString());
    }

    private void assertCreateMrIntentUnsentFailure(GitWriteResult<GitBranchPort.MrEvidence> result,
                                                    GitWriteResult.Outcome outcome) {
        assertEquals(outcome, result.outcome());
        assertCreateMrIntentIdentity(result.evidence(), SHA);
        assertAll(
                () -> assertNull(result.evidence().iid()),
                () -> assertNull(result.evidence().url()),
                () -> assertNull(result.evidence().mergeSha()),
                () -> assertNull(result.evidence().disposition()));
        assertFalse(GitMergeWorkflow.isValidCheckpoint(result.evidence()));
        verifyNoWrites();
    }

    private void assertCreateMrIntentIdentity(GitBranchPort.MrEvidence evidence, String frozenSha) {
        assertNotNull(evidence);
        assertAll(
                () -> assertEquals("7", evidence.projectId()),
                () -> assertEquals("source", evidence.sourceBranch()),
                () -> assertEquals("target", evidence.targetBranch()),
                () -> assertEquals(frozenSha, evidence.sourceSha()));
    }
    // End B9 independent contract.

    private GitBranchPort.MrEvidence checkpoint(MrDisposition disposition, String mergeSha) {
        return new GitBranchPort.MrEvidence("7", "source", "target", "17", ready.url(), SHA, mergeSha, disposition, null);
    }

    private GitMergeWorkflow workflow() { return new GitMergeWorkflow(port, 2, () -> {}); }
    private void verifyNoWrites() {
        verify(port, never()).create(anyString(), anyString(), anyString(), anyString());
        verify(port, never()).merge(anyString(), any(), anyString());
    }
    private static MergeRequest mr(String id, String sourceSha, State state, Readiness readiness, MergeBlockReason reason) {
        return new MergeRequest(id, "https://gitlab.test/project/-/merge_requests/" + id, "7", "source", "target",
                sourceSha, state == State.MERGED ? MERGE_SHA : null, state, readiness, reason, reason == null ? null : reason.name());
    }
}
