package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real identity resolver + HTTP interceptor + adapter; only the local HTTP provider is simulated. */
@WireMockTest(httpPort = 0)
class GitLabReleaseEvidenceTest {
    private static final String SHA = "a".repeat(40);
    private static final String OTHER_SHA = "b".repeat(40);
    private static final String PROJECT = "/api/v4/projects/acme%2Freleasehub";
    private static final String NUMERIC = "/api/v4/projects/7";
    private static final String TAG = "v1.2.0";
    private final GitOperationJournalPort journal = mock(GitOperationJournalPort.class);
    private GitBranchPort adapter;
    private GitLabGitBranchAdapter target;
    private GitWriteHttpInterceptor interceptor;
    private String cloneUrl;
    private String baseUrl;
    private CodeRepositoryPort repos;
    private SettingsPort settings;

    @BeforeEach void setup(WireMockRuntimeInfo wm) {
        baseUrl = wm.getHttpBaseUrl();
        cloneUrl = baseUrl + "/acme/releasehub.git";
        var repo = mock(CodeRepository.class);
        when(repo.getId()).thenReturn(RepoId.of("repo"));
        when(repo.getCloneUrl()).thenReturn(cloneUrl);
        when(repo.getGitProvider()).thenReturn(GitProvider.GITLAB);
        repos = mock(CodeRepositoryPort.class);
        when(repos.findByCloneUrls(any(), eq(GitProvider.GITLAB))).thenReturn(List.of(repo));
        settings = mock(SettingsPort.class);
        when(settings.getGitLab()).thenReturn(Optional.of(new SettingsPort.SettingsGitLab(baseUrl, "fixture-token")));
        var builder = new RestTemplateBuilder();
        var identities = new GitRemoteIdentityResolver(repos, settings, builder);
        var properties = new GitSafetyProperties();
        var sessions = new GitSafetySessions(journal);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(journal.begin(any())).thenAnswer(invocation -> {
            GitOperationJournalPort.Command command = invocation.getArgument(0);
            return new GitOperationJournalPort.Operation(command.operationId(), command.repoId(), command.remoteKey(),
                    command.provider(), command.baseUrl(), command.projectId(), command.action(), command.targetJson(),
                    command.runId(), command.operator(), "INTENT_RECORDED", 0, java.time.Instant.EPOCH,
                    java.time.Instant.EPOCH, null, true);
        });
        interceptor = new GitWriteHttpInterceptor(properties, sessions, journal, identities, new ObjectMapper(), transactions);
        target = new GitLabGitBranchAdapter(builder, identities);
        target.installWriteSafety(interceptor);
        var decorator = new GitSafetyBeanDecorator(provider(properties), provider(sessions), provider(journal),
                provider(repos), provider(transactions));
        adapter = (GitBranchPort) decorator.postProcessAfterInitialization(target, "releaseEvidenceFixture");
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(okJson("{\"id\":7}")));
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/commits/" + SHA))
                .willReturn(okJson("{\"id\":\"" + SHA + "\"}")));
    }

    @AfterEach void shouldKeepInternalPreconditionsOffTheWire() {
        verify(0, anyRequestedFor(anyUrl()).withHeader(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID, matching(".*")));
        verify(0, anyRequestedFor(anyUrl()).withHeader(GitRemoteIdentityResolver.EXPECTED_SHA, matching(".*")));
        verify(0, postRequestedFor(urlPathMatching("/api/v4/projects/acme.*")));
    }

    @Test void shouldReadExactTagAndCommitFromConfirmedNumericProject() {
        tagRead(TAG, SHA);
        var result = adapter.readTag(cloneUrl, "fixture-token", "7", TAG);
        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(new GitBranchPort.TagEvidence("7", TAG, SHA), result.evidence());
        verify(1, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)));
        verifyNoInteractions(journal);
    }

    @Test void shouldReportOnlyRealTag404AsNotFound() {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)).willReturn(notFound()));
        assertEquals(GitReadResult.Status.NOT_FOUND, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        verifyNoInteractions(journal);
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500})
    void shouldNotTurnProjectResolutionFailureIntoMissingTag(int status) {
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(aResponse().withStatus(status)));
        assertEquals(GitReadResult.Status.UNAVAILABLE, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        verify(0, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)));
        verifyNoInteractions(journal);
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 408, 500})
    void shouldPreserveTagReadFailureWithoutClaimingAbsence(int status) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)).willReturn(aResponse().withStatus(status)));
        assertEquals(status == 401 || status == 403 ? GitReadResult.Status.FORBIDDEN : GitReadResult.Status.UNAVAILABLE,
                adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        verifyNoInteractions(journal);
    }

    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "{\"name\":\"v1.2.0\"}", "{\"name\":\"v1.2.0\",\"commit\":{\"id\":\"bad\"}}"})
    void shouldKeepIncompleteTagReadUnresolved(String body) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)).willReturn(okJson(body)));
        assertEquals(GitReadResult.Status.INCOMPLETE, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        verifyNoInteractions(journal);
    }

    @Test void shouldRejectDifferentTagIdentity() {
        tagRead("different", SHA);
        assertEquals(GitReadResult.Status.MISMATCH, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
    }

    @Test void shouldCreateTagAtTheExactConfirmedCommit() {
        stubFor(post(urlPathEqualTo(NUMERIC + "/repository/tags")).willReturn(created().withHeader("Content-Type", "application/json")
                .withBody(tagBody(TAG, SHA))));
        var result = adapter.createTag(cloneUrl, "fixture-token", "7", TAG, SHA, "release");
        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(new GitBranchPort.TagEvidence("7", TAG, SHA), result.evidence());
        verify(1, postRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags"))
                .withRequestBody(matchingJsonPath("$.ref", equalTo(SHA))));
        verify(journal).completeSession(anyString(), eq(true));
    }

    @Test void shouldKeepWrongTagCommitUnknownAndRetainGuard() {
        stubFor(post(urlPathEqualTo(NUMERIC + "/repository/tags")).willReturn(created().withHeader("Content-Type", "application/json")
                .withBody(tagBody(TAG, OTHER_SHA))));
        assertEquals(GitWriteResult.Outcome.UNKNOWN, adapter.createTag(cloneUrl, "fixture-token", "7", TAG, SHA, "release").outcome());
        verify(journal).finish(anyString(), isNull(), eq(GitWriteResult.Outcome.UNKNOWN), anyString(), anyString());
        verify(journal).completeSession(anyString(), eq(false));
    }

    @Test void shouldNotRetryTagNameRaceAutomatically() {
        stubFor(post(urlPathEqualTo(NUMERIC + "/repository/tags")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"sensitive fixture\"}")));
        var result = adapter.createTag(cloneUrl, "fixture-token", "7", TAG, SHA, "release");
        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals("HTTP_409", result.reason());
        verify(1, postRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags")));
        verify(0, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)));
        verify(journal).completeSession(anyString(), eq(true));
    }

    @Test void shouldKeepTagAndPipelineBoundToMrProjectWhenNamespaceIsReused() {
        tagRead(TAG, SHA);
        assertEquals(GitReadResult.Status.FOUND, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(okJson("{\"id\":8}")));

        var tagError = assertThrows(BusinessException.class,
                () -> adapter.createTag(cloneUrl, "fixture-token", "7", TAG, SHA, "release"));
        var pipelineError = assertThrows(BusinessException.class,
                () -> adapter.triggerPipeline(cloneUrl, "fixture-token", "7", TAG, SHA));
        assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, tagError.getErrorCode());
        assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, pipelineError.getErrorCode());
        assertEquals(GitReadResult.Status.UNAVAILABLE, adapter.readTag(cloneUrl, "fixture-token", "7", TAG).status());
        verify(0, postRequestedFor(anyUrl()));
        verify(journal, never()).begin(any());
    }

    @Test void shouldPreserveCompletePipelineEvidenceAndEncodedRef() throws Exception {
        String ref = "release/v1+candidate";
        var body = new ObjectMapper().readTree(pipelineBody()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("ref", ref);
        stubFor(post(urlPathEqualTo(NUMERIC + "/pipeline")).willReturn(created().withHeader("Content-Type", "application/json")
                .withBody(body.toString())));
        var result = adapter.triggerPipeline(cloneUrl, "fixture-token", "7", ref, SHA);
        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(new GitBranchPort.PipelineEvidence("7", "321", ref, SHA,
                "https://gitlab.test/acme/releasehub/-/pipelines/321"), result.evidence());
        verify(1, postRequestedFor(urlEqualTo(NUMERIC + "/pipeline?ref=release%2Fv1%2Bcandidate")));
        var command = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal).begin(command.capture());
        assertEquals(SHA, new ObjectMapper().readTree(command.getValue().targetJson()).path("sha").asText());
        verify(journal).completeSession(anyString(), eq(true));
    }

    @ParameterizedTest @MethodSource("invalidPipelineResponses")
    void shouldRetainGuardForIncompleteOrMismatchedPipelineEvidence(String body) {
        stubFor(post(urlPathEqualTo(NUMERIC + "/pipeline")).willReturn(created().withHeader("Content-Type", "application/json").withBody(body)));
        var result = adapter.triggerPipeline(cloneUrl, "fixture-token", "7", TAG, SHA);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
        assertNull(result.evidence());
        verify(1, postRequestedFor(urlPathEqualTo(NUMERIC + "/pipeline")));
        verify(journal).finish(anyString(), isNull(), eq(GitWriteResult.Outcome.UNKNOWN), anyString(), anyString());
        verify(journal).completeSession(anyString(), eq(false));
    }

    @ParameterizedTest @ValueSource(ints = {400, 401, 403, 404, 409, 422, 408, 500, 503})
    void shouldDistinguishConfirmedPipelineRejectionFromUnknownOutcome(int status) {
        stubFor(post(urlPathEqualTo(NUMERIC + "/pipeline")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody("{\"message\":\"sensitive provider fixture\"}")));
        var result = adapter.triggerPipeline(cloneUrl, "fixture-token", "7", TAG, SHA);
        var expected = status < 500 && status != 408 ? GitWriteResult.Outcome.REJECTED : GitWriteResult.Outcome.UNKNOWN;
        assertEquals(expected, result.outcome());
        assertFalse(result.reason().contains("sensitive"));
        verify(1, postRequestedFor(urlPathEqualTo(NUMERIC + "/pipeline")));
        verify(journal).completeSession(anyString(), eq(expected == GitWriteResult.Outcome.REJECTED));
    }

    @Test void shouldKeepLostPipelineResponseUnknownWithoutRetry() {
        var http = new org.springframework.web.client.RestTemplate();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build();
        target.setRestTemplate(http);
        target.installWriteSafety(interceptor);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(baseUrl + NUMERIC + "/pipeline?ref=" + TAG))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(new java.net.SocketTimeoutException("lost")));
        assertEquals(GitWriteResult.Outcome.UNKNOWN, adapter.triggerPipeline(cloneUrl, "fixture-token", "7", TAG, SHA).outcome());
        verify(journal).sending(anyString(), isNull());
        verify(journal).completeSession(anyString(), eq(false));
        server.verify();
    }

    @Test void shouldRejectUnboundProjectOrMovingTagRefBeforeDispatch() {
        assertEquals(ErrorCode.GIT_OPERATION_INVALID, assertThrows(BusinessException.class,
                () -> adapter.createTag(cloneUrl, "fixture-token", "7", TAG, "main", "release")).getErrorCode());
        assertEquals(ErrorCode.GIT_OPERATION_INVALID, assertThrows(BusinessException.class,
                () -> adapter.triggerPipeline(cloneUrl, "fixture-token", "namespace", TAG, SHA)).getErrorCode());
        assertEquals(ErrorCode.GIT_OPERATION_INVALID, assertThrows(BusinessException.class,
                () -> adapter.triggerPipeline(cloneUrl, "fixture-token", "7", TAG, "bad")).getErrorCode());
        verify(0, postRequestedFor(anyUrl()));
        verifyNoInteractions(journal);
    }

    private void tagRead(String name, String sha) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)).willReturn(okJson(tagBody(name, sha))));
    }
    private static String tagBody(String name, String sha) { return "{\"name\":\"" + name + "\",\"commit\":{\"id\":\"" + sha + "\"}}"; }
    private static String pipelineBody() {
        return """
                {"id":321,"project_id":7,"ref":"v1.2.0","sha":"%s",
                 "web_url":"https://gitlab.test/acme/releasehub/-/pipelines/321"}
                """.formatted(SHA);
    }
    private static Stream<String> invalidPipelineResponses() throws Exception {
        var json = new ObjectMapper();
        var cases = new java.util.ArrayList<String>();
        for (String field : List.of("id", "project_id", "ref", "sha", "web_url")) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(pipelineBody());
            body.remove(field);cases.add(body.toString());
        }
        for (var entry : java.util.Map.of("project_id", "8", "ref", "other", "sha", OTHER_SHA,
                "id", "0", "web_url", "https://gitlab.test/path?token=sensitive").entrySet()) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(pipelineBody());
            if (entry.getKey().equals("project_id") || entry.getKey().equals("id")) body.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            else body.put(entry.getKey(), entry.getValue());
            cases.add(body.toString());
        }
        return cases.stream();
    }
    // B13 independent contract: namespace identity needs actual HTTP 200 and a positive integral JSON id.
    @ParameterizedTest @ValueSource(ints = {201, 206})
    void projectIdentityRejectsNon200NamespaceResponse(int status) {
        assertProjectIdentityRejected(status, "{\"id\":7}");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"id\":\"7\"}", "{\"id\":7.0}", "{\"id\":7.5}", "{\"id\":0}", "{\"id\":-1}",
            "{}", "{\"id\":null}", "[{\"id\":7}]", ""})
    void projectIdentityRejectsUntypedOrNonPositiveId(String body) {
        assertProjectIdentityRejected(200, body);
    }

    @Test
    void projectIdentityKeepsLargeIntegralProject() {
        String project = "9223372036854775808";
        String numericTag = "/api/v4/projects/" + project + "/repository/tags/" + TAG;
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(okJson(
                "{\"id\":" + project + ",\"path_with_namespace\":\"acme/releasehub\"}")));
        stubFor(get(urlPathEqualTo(numericTag)).willReturn(okJson(tagBody(TAG, SHA))));

        var result = adapter.readTag(cloneUrl, "fixture-token", project, TAG);

        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(new GitBranchPort.TagEvidence(project, TAG, SHA), result.evidence());
        verify(1, getRequestedFor(urlPathEqualTo(PROJECT)).withHeader("PRIVATE-TOKEN", equalTo("fixture-token")));
        verify(1, getRequestedFor(urlPathEqualTo(numericTag)).withHeader("PRIVATE-TOKEN", equalTo("fixture-token")));
        verifyNoInteractions(journal);
    }

    @Test
    void projectIdentityKeepsNamespaceNetworkFailureUnavailable() {
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(aResponse()
                .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        var result = adapter.readTag(cloneUrl, "fixture-token", "7", TAG);

        assertEquals(GitReadResult.Status.UNAVAILABLE, result.status());
        assertNull(result.evidence());
        assertFalse(findAll(getRequestedFor(urlPathEqualTo(PROJECT))
                .withHeader("PRIVATE-TOKEN", equalTo("fixture-token"))).isEmpty());
        verify(0, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)));
        verifyNoInteractions(journal);
    }

    @ParameterizedTest @ValueSource(strings = {"CONFIGURED_BASE", "NO_REPOSITORY", "AMBIGUOUS_REPOSITORY"})
    void projectIdentityKeepsLocalAdmissionBeforeNamespaceRead(String shape) {
        switch (shape) {
            case "CONFIGURED_BASE" -> when(settings.getGitLab()).thenReturn(Optional.of(
                    new SettingsPort.SettingsGitLab(baseUrl + "/different", "fixture-token")));
            case "NO_REPOSITORY" -> when(repos.findByCloneUrls(any(), eq(GitProvider.GITLAB))).thenReturn(List.of());
            case "AMBIGUOUS_REPOSITORY" -> when(repos.findByCloneUrls(any(), eq(GitProvider.GITLAB)))
                    .thenReturn(List.of(mock(CodeRepository.class), mock(CodeRepository.class)));
            default -> throw new AssertionError(shape);
        }
        tagRead(TAG, SHA);

        var result = adapter.readTag(cloneUrl, "fixture-token", "7", TAG);

        assertEquals(GitReadResult.Status.UNAVAILABLE, result.status(), shape);
        assertNull(result.evidence(), shape);
        verify(0, getRequestedFor(urlPathEqualTo(PROJECT)));
        verify(0, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG)));
        verifyNoInteractions(journal);
    }

    private void assertProjectIdentityRejected(int status, String body) {
        stubFor(get(urlPathEqualTo(PROJECT)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
        // A valid downstream response lets the baseline expose its actual result; the contract still requires zero reads.
        tagRead(TAG, SHA);

        var result = adapter.readTag(cloneUrl, "fixture-token", "7", TAG);

        assertAll(
                () -> assertEquals(GitReadResult.Status.UNAVAILABLE, result.status()),
                () -> assertNull(result.evidence()),
                () -> verify(0, getRequestedFor(urlPathEqualTo(NUMERIC + "/repository/tags/" + TAG))));
        verify(1, getRequestedFor(urlPathEqualTo(PROJECT)).withHeader("PRIVATE-TOKEN", equalTo("fixture-token")));
        verifyNoInteractions(journal);
    }

    // End B13 independent identity contract.

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> result = mock(ObjectProvider.class);when(result.getObject()).thenReturn(bean);return result;
    }
}
