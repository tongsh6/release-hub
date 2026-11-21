package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitoperation.*;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitWriteHttpInterceptorTest {
    private final GitOperationJournalPort journal=mock(GitOperationJournalPort.class);
    private final GitRemoteIdentityResolver identities=mock(GitRemoteIdentityResolver.class);
    private final GitSafetyProperties properties=new GitSafetyProperties();
    private GitWriteHttpInterceptor interceptor;
    @BeforeEach void setup() {
        properties.setEnabled(true);
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var endpoint=new GitRemoteIdentityResolver.Endpoint("GITLAB","https://gitlab.invalid","acme/repo","https://gitlab.invalid/api/v4/projects/17","/merge_requests");
        when(identities.resolve(any(),any())).thenReturn(new GitRemoteIdentityResolver.Identity("repo","mapping","a".repeat(64),"GITLAB","https://gitlab.invalid","17",endpoint));
        when(journal.begin(any())).thenAnswer(i->((GitOperationJournalPort.Command)i.getArgument(0)).operationId());
        interceptor=new GitWriteHttpInterceptor(properties,new GitSafetySessions(journal),journal,identities,new ObjectMapper(),manager);
    }
    @Test void shouldNotReleasePipelineGuardBeforeConsumerRejectsDifferentShaText() throws Exception {
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid", "acme/repo",
                "https://gitlab.invalid/api/v4/projects/acme%2Frepo", "/pipeline");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        var request = new MockClientHttpRequest(HttpMethod.POST, URI.create(endpoint.projectEndpoint() + "/pipeline?ref=v1"));
        request.getHeaders().set("X-ReleaseHub-Expected-Project-Id", "17");
        request.getHeaders().set("X-ReleaseHub-Expected-Commit-Sha", "a".repeat(40));
        byte[] responseBody = ("""
                {"id":321,"project_id":17,"ref":"v1","sha":"%s",
                 "web_url":"https://gitlab.invalid/acme/repo/-/pipelines/321"}
                """).formatted("A".repeat(40)).getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> interceptor.intercept(request, new byte[0],
                (sent, body) -> new MockClientHttpResponse(responseBody, HttpStatus.CREATED)));
        verify(journal).finish(anyString(),isNull(), eq(GitWriteResult.Outcome.UNKNOWN), anyString(), anyString());
        verify(journal).completeSession(anyString(), eq(false));
    }


    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "REJECTED", "INCOMPLETE", "IO"})
    void mergeIntentUsesTheJournalOperationIdForSendingAndFinish(String outcome) throws Exception {
        String originalId = "11111111-1111-4111-8111-111111111111";
        doReturn(originalId).when(journal).begin(any());
        try (var context = GitOperationContext.withTask(mergeIntentClaim())) {
            executeMergeIntentOutcome(outcome);
        }

        var command = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal).begin(command.capture());
        assertNotEquals(originalId, command.getValue().operationId());
        assertAll(
                () -> assertEquals(originalId, singleJournalInvocation("sending").getArguments()[0]),
                () -> assertEquals(originalId, singleJournalInvocation("finish").getArguments()[0]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "REJECTED", "INCOMPLETE", "IO"})
    void mergeIntentCarriesTheCurrentTaskFenceToSendAndFinish(String outcome) throws Exception {
        var claim = mergeIntentClaim();
        try (var context = GitOperationContext.withTask(claim)) {
            executeMergeIntentOutcome(outcome);
        }

        var command = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal).begin(command.capture());
        assertEquals(claim.fence(), command.getValue().taskFence());
        // Inspect actual public calls so the RED compiles before the explicit-fence signature exists.
        assertAll(
                () -> assertTrue(java.util.Arrays.asList(singleJournalInvocation("sending").getArguments())
                        .contains(claim.fence()), "sending must receive the actual caller fence"),
                () -> assertTrue(java.util.Arrays.asList(singleJournalInvocation("finish").getArguments())
                        .contains(claim.fence()), "finish must receive the actual caller fence"));
    }

    @Test
    void mergeIntentPersistsTheCompleteTypedRequest() throws Exception {
        String sha = "a".repeat(40);
        String message = "Custom merge \"message\" | keep\nthis exact text";
        var request = mergeIntentRequest(sha, null);
        byte[] body = mergeIntentBody(sha, message).toString().getBytes(StandardCharsets.UTF_8);
        try (var response = interceptor.intercept(request, body, (sent, bytes) -> {
            assertEquals("sensitive-intent-token", sent.getHeaders().getFirst("PRIVATE-TOKEN"));
            assertNull(sent.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID));
            assertNull(sent.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_SHA));
            assertEquals(new ObjectMapper().readTree(body), new ObjectMapper().readTree(bytes));
            assertNull(sent.getURI().getRawQuery());
            return mergeIntentResponse("APPLIED", sha);
        })) {
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        var command = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal).begin(command.capture());
        var target = new ObjectMapper().readTree(command.getValue().targetJson());
        assertEquals("MERGE_MR", command.getValue().action());
        assertAll(
                () -> assertTrue(target.path("sha").isTextual()),
                () -> assertEquals(sha, target.path("sha").textValue()),
                () -> assertTrue(target.path("merge_commit_message").isTextual()),
                () -> assertEquals(message, target.path("merge_commit_message").textValue()),
                () -> assertTrue(target.path("should_remove_source_branch").isBoolean()),
                () -> assertFalse(target.path("should_remove_source_branch").booleanValue()),
                () -> assertEquals("PUT", target.path("method").textValue()),
                () -> assertEquals("/merge_requests/7/merge", target.path("suffix").textValue()),
                () -> assertFalse(command.getValue().toString().contains("sensitive-intent-token")));
    }

    @Test
    void mergeIntentFingerprintIsStableAcrossJsonFieldOrder() throws Exception {
        String sha = "a".repeat(40);
        String message = "Merge feature/IT-1 into release/RW-1";
        var first = mergeIntentBody(sha, message);
        var reordered = new ObjectMapper().createObjectNode().put("should_remove_source_branch", false)
                .put("merge_commit_message", message).put("sha", sha);
        assertNotEquals(first.toString(), reordered.toString());
        for (var body : java.util.List.of(first, reordered)) {
            try (var response = interceptor.intercept(mergeIntentRequest(sha, null),
                    body.toString().getBytes(StandardCharsets.UTF_8), (sent, bytes) -> {
                        assertEquals(first, new ObjectMapper().readTree(bytes));
                        return mergeIntentResponse("APPLIED", sha);
                    })) {
                assertEquals(HttpStatus.OK, response.getStatusCode());
            }
        }

        var commands = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal, times(2)).begin(commands.capture());
        assertEquals(commands.getAllValues().get(0).requestHash(), commands.getAllValues().get(1).requestHash());
    }

    @Test
    void mergeIntentFingerprintDistinguishesDifferentMessages() throws Exception {
        String sha = "a".repeat(40);
        for (String message : java.util.List.of("Merge | one\nexact message", "Merge | two\nexact message")) {
            try (var response = interceptor.intercept(mergeIntentRequest(sha, null),
                    mergeIntentBody(sha, message).toString().getBytes(StandardCharsets.UTF_8),
                    (sent, bytes) -> mergeIntentResponse("APPLIED", sha))) {
                assertEquals(HttpStatus.OK, response.getStatusCode());
            }
        }

        var commands = org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);
        verify(journal, times(2)).begin(commands.capture());
        assertNotEquals(commands.getAllValues().get(0).requestHash(), commands.getAllValues().get(1).requestHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_SHA", "MISSING_MESSAGE", "MISSING_REMOVE", "MESSAGE_NUMBER", "MESSAGE_NULL",
            "REMOVE_STRING", "REMOVE_NULL", "REMOVE_TRUE", "SHA_NUMBER", "UNKNOWN_FIELD"})
    void mergeIntentRejectsIncompleteOrIllTypedRequestsBeforeIntent(String variant) throws Exception {
        String sha = "SHA_NUMBER".equals(variant) ? "1".repeat(40) : "a".repeat(40);
        var body = mergeIntentBody(sha, "Merge feature/IT-1 into release/RW-1");
        switch (variant) {
            case "MISSING_SHA" -> body.remove("sha");
            case "MISSING_MESSAGE" -> body.remove("merge_commit_message");
            case "MISSING_REMOVE" -> body.remove("should_remove_source_branch");
            case "MESSAGE_NUMBER" -> body.put("merge_commit_message", 7);
            case "MESSAGE_NULL" -> body.putNull("merge_commit_message");
            case "REMOVE_STRING" -> body.put("should_remove_source_branch", "false");
            case "REMOVE_NULL" -> body.putNull("should_remove_source_branch");
            case "REMOVE_TRUE" -> body.put("should_remove_source_branch", true);
            case "SHA_NUMBER" -> body.put("sha", new java.math.BigInteger(sha));
            case "UNKNOWN_FIELD" -> body.put("squash", true);
            default -> throw new IllegalArgumentException(variant);
        }
        var sent = new AtomicInteger();
        try (var context = GitOperationContext.withTask(mergeIntentClaim())) {
            var error = assertThrows(BusinessException.class,
                    () -> interceptor.intercept(mergeIntentRequest(sha, null),
                            body.toString().getBytes(StandardCharsets.UTF_8), (request, bytes) -> {
                                sent.incrementAndGet();
                                return mergeIntentResponse("APPLIED", sha);
                            }));
            assertEquals(ErrorCode.GIT_OPERATION_INVALID, error.getErrorCode());
        }
        assertEquals(0, sent.get());
        verifyNoInteractions(journal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "squash=true"})
    void mergeIntentRejectsQueriesBeforeIntent(String query) throws Exception {
        String sha = "a".repeat(40);
        var request = mergeIntentRequest(sha, query);
        assertNotNull(request.getURI().getRawQuery());
        var sent = new AtomicInteger();
        try (var context = GitOperationContext.withTask(mergeIntentClaim())) {
            var error = assertThrows(BusinessException.class,
                    () -> interceptor.intercept(request,
                            mergeIntentBody(sha, "Merge feature/IT-1 into release/RW-1")
                                    .toString().getBytes(StandardCharsets.UTF_8),
                            (outbound, bytes) -> {
                                sent.incrementAndGet();
                                return mergeIntentResponse("APPLIED", sha);
                            }));
            assertEquals(ErrorCode.GIT_OPERATION_INVALID, error.getErrorCode());
        }
        assertEquals(0, sent.get());
        verifyNoInteractions(journal);
    }

    @Test
    void mergeIntentDoesNotWriteOrFinishWhenSendingIsRefused() throws Exception {
        doThrow(new IllegalStateException("sender lost its current claim")).when(journal)
                .sending(anyString(), eq(mergeIntentClaim().fence()));
        var sent = new AtomicInteger();
        try (var context = GitOperationContext.withTask(mergeIntentClaim())) {
            assertThrows(IllegalStateException.class,
                    () -> interceptor.intercept(mergeIntentRequest("a".repeat(40), null),
                            mergeIntentBody("a".repeat(40), "Merge feature/IT-1 into release/RW-1")
                                    .toString().getBytes(StandardCharsets.UTF_8),
                            (request, bytes) -> {
                                sent.incrementAndGet();
                                return mergeIntentResponse("APPLIED", "a".repeat(40));
                            }));
        }
        assertEquals(0, sent.get());
        assertTrue(mockingDetails(journal).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().equals("finish")));
    }

    private io.releasehub.application.run.RunPort.TaskClaim mergeIntentClaim() {
        var fence = new io.releasehub.application.run.RunPort.TaskFence(
                "22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333");
        return new io.releasehub.application.run.RunPort.TaskClaim(fence, "run-intent", "item-intent",
                io.releasehub.domain.repo.RepoId.of("repo"), io.releasehub.domain.run.ActionType.TRY_MERGE,
                java.time.Instant.parse("2026-11-21T00:00:00Z"), "tester", java.util.Map.of(
                        "git.projectId", "17", "git.mrIid", "7",
                        "git.mrUrl", "https://gitlab.invalid/acme/repo/-/merge_requests/7",
                        "git.sourceBranch", "feature/IT-1", "git.targetBranch", "release/RW-1",
                        "git.sourceSha", "a".repeat(40), "git.mrDisposition", "CREATED",
                        "plan.featureBranch", "feature/IT-1", "plan.releaseBranch", "release/RW-1"));
    }

    private MockClientHttpRequest mergeIntentRequest(String sha, String query) {
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid", "acme/repo",
                "https://gitlab.invalid/api/v4/projects/17", "/merge_requests/7/merge");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        var request = new MockClientHttpRequest(HttpMethod.PUT, URI.create(
                endpoint.projectEndpoint() + endpoint.suffix() + (query == null ? "" : "?" + query)));
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID, "17");
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_SHA, sha);
        request.getHeaders().set("PRIVATE-TOKEN", "sensitive-intent-token");
        return request;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mergeIntentBody(String sha, String message) {
        return new ObjectMapper().createObjectNode().put("sha", sha).put("merge_commit_message", message)
                .put("should_remove_source_branch", false);
    }

    private MockClientHttpResponse mergeIntentResponse(String outcome, String sha) throws IOException {
        if ("IO".equals(outcome)) throw new IOException("response lost after dispatch");
        if ("REJECTED".equals(outcome)) return new MockClientHttpResponse(
                "{\"message\":\"Permission denied\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.FORBIDDEN);
        if ("INCOMPLETE".equals(outcome)) return new MockClientHttpResponse(
                "{\"iid\":7}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        var body = new ObjectMapper().createObjectNode().put("iid", 7).put("project_id", 17)
                .put("source_project_id", 17).put("target_project_id", 17)
                .put("source_branch", "feature/IT-1").put("target_branch", "release/RW-1")
                .put("sha", sha).put("state", "merged").put("merge_commit_sha", "b".repeat(40))
                .put("web_url", "https://gitlab.invalid/acme/repo/-/merge_requests/7");
        return new MockClientHttpResponse(body.toString().getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
    }

    private void executeMergeIntentOutcome(String outcome) throws Exception {
        String sha = "a".repeat(40);
        var request = mergeIntentRequest(sha, null);
        byte[] body = mergeIntentBody(sha, "Merge feature/IT-1 into release/RW-1")
                .toString().getBytes(StandardCharsets.UTF_8);
        var sent = new AtomicInteger();
        org.springframework.http.client.ClientHttpRequestExecution execution = (outbound, bytes) -> {
            sent.incrementAndGet();
            return mergeIntentResponse(outcome, sha);
        };
        if ("IO".equals(outcome) || "INCOMPLETE".equals(outcome)) {
            assertThrows(IOException.class, () -> interceptor.intercept(request, body, execution));
        } else {
            try (var response = interceptor.intercept(request, body, execution)) {
                assertEquals("REJECTED".equals(outcome) ? HttpStatus.FORBIDDEN : HttpStatus.OK, response.getStatusCode());
            }
        }
        assertEquals(1, sent.get());
    }

    private org.mockito.invocation.Invocation singleJournalInvocation(String method) {
        var calls = mockingDetails(journal).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals(method)).toList();
        assertEquals(1, calls.size(), method + " must have exactly one call");
        return calls.getFirst();
    }

    private MockClientHttpRequest request(){
        var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://gitlab.invalid/api/v4/projects/acme%2Frepo/merge_requests"));
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID,"17");
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_SHA,"a".repeat(40));
        return request;
    }
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void shouldBlockEveryWriteWhenSafetyDispatchIsDisabled(String method) throws Exception {
        properties.setEnabled(false);
        var disabled = new GitWriteHttpInterceptor(properties, new GitSafetySessions(journal), journal,
                identities, new ObjectMapper(), mock(PlatformTransactionManager.class));
        var request = new MockClientHttpRequest(HttpMethod.valueOf(method), URI.create("https://gitlab.invalid/api/v4/projects/17/merge_requests"));
        var sent = new AtomicInteger();

        var error = assertThrows(BusinessException.class, () -> disabled.intercept(request, new byte[0], (r, body) -> {
            sent.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        }));

        assertEquals(ErrorCode.GIT_WRITE_BLOCKED, error.getErrorCode());
        assertEquals(0, sent.get());
        verifyNoInteractions(journal, identities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldKeepReadOnlyHttpAvailableWhenSafetyDispatchIsDisabled(String method) throws Exception {
        properties.setEnabled(false);
        var disabled = new GitWriteHttpInterceptor(properties, new GitSafetySessions(journal), journal,
                identities, new ObjectMapper(), mock(PlatformTransactionManager.class));
        var request = new MockClientHttpRequest(HttpMethod.valueOf(method), URI.create("https://gitlab.invalid/api/v4/projects/17"));
        var sent = new AtomicInteger();

        try (var response = disabled.intercept(request, new byte[0], (r, body) -> {
            sent.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        })) {
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
        assertEquals(1, sent.get());
        verifyNoInteractions(journal, identities);
    }

    @Test void shouldPinPipelineWriteToVerifiedProjectAndPreserveRawQuery() throws Exception {
        String base = "https://gitlab.invalid/gitlab";
        String query = "ref=release%2FRW%201&hint=a+b";
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", base, "acme/repo",
                base + "/api/v4/projects/acme%2Frepo", "/pipeline");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", base, "17", endpoint));
        var original = new MockClientHttpRequest(HttpMethod.POST,
                URI.create(endpoint.projectEndpoint() + endpoint.suffix() + "?" + query));
        original.getHeaders().set("PRIVATE-TOKEN", "sensitive-token-fixture");
        original.getHeaders().set("X-Correlation-ID", "correlation-fixture");
        original.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID, "17");
        original.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_SHA, "a".repeat(40));
        byte[] input = "{\"variables\":[{\"key\":\"MODE\",\"value\":\"release\"}]}".getBytes(StandardCharsets.UTF_8);
        var dispatched = new java.util.concurrent.atomic.AtomicReference<org.springframework.http.HttpRequest>();
        var sentBody = new java.util.concurrent.atomic.AtomicReference<byte[]>();

        try (var response = interceptor.intercept(original, input, (request, body) -> {
            dispatched.set(request);
            sentBody.set(body);
            return new MockClientHttpResponse(("""
                    {"id":9,"project_id":17,"ref":"release/RW 1","sha":"%s",
                     "web_url":"https://gitlab.invalid/gitlab/acme/repo/-/pipelines/9"}
                    """).formatted("a".repeat(40)).getBytes(StandardCharsets.UTF_8), HttpStatus.CREATED);
        })) {
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }

        assertEquals(base + "/api/v4/projects/17/pipeline?" + query, dispatched.get().getURI().toString());
        assertEquals(query, dispatched.get().getURI().getRawQuery());
        assertEquals(HttpMethod.POST, dispatched.get().getMethod());
        assertEquals("sensitive-token-fixture", dispatched.get().getHeaders().getFirst("PRIVATE-TOKEN"));
        assertEquals("correlation-fixture", dispatched.get().getHeaders().getFirst("X-Correlation-ID"));
        assertNull(dispatched.get().getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID));
        assertNull(dispatched.get().getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_SHA));
        assertEquals(new ObjectMapper().readTree(input), new ObjectMapper().readTree(sentBody.get()));
        assertEquals(sentBody.get().length, dispatched.get().getHeaders().getContentLength());
    }

    @Test void shouldKeepEncodedFileSuffixWhenPinningWriteToVerifiedProject() throws Exception {
        String suffix = "/repository/files/modules%2Fpom%20one.xml";
        String revision = "a".repeat(40);
        String content = "<project>changed</project>";
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid", "acme/repo",
                "https://gitlab.invalid/api/v4/projects/acme%2Frepo", suffix);
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "b".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        when(identities.metadata(any(), eq(suffix), eq("main"), any())).thenReturn(new ObjectMapper().createObjectNode()
                .put("last_commit_id", revision).put("content_sha256", GitRemoteIdentityResolver.hash(content)));
        var request = new MockClientHttpRequest(HttpMethod.PUT, URI.create(endpoint.projectEndpoint() + suffix));
        byte[] body = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                "branch", "main", "content", content, "commit_message", "update", "last_commit_id", revision));
        var dispatched = new java.util.concurrent.atomic.AtomicReference<URI>();
        var sentBody = new java.util.concurrent.atomic.AtomicReference<byte[]>();

        try (var response = interceptor.intercept(request, body, (sent, bytes) -> {
            dispatched.set(sent.getURI());
            sentBody.set(bytes);
            return new MockClientHttpResponse("{\"file_path\":\"modules/pom one.xml\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        })) {
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        assertEquals("/api/v4/projects/17" + suffix, dispatched.get().getRawPath());
        var sent = new ObjectMapper().readTree(sentBody.get());
        assertEquals(revision, sent.path("last_commit_id").asText());
        assertEquals(content, sent.path("content").asText());
    }

    @Test void shouldSendNothingWhenIntentCommitFails() {
        doThrow(new IllegalStateException("模拟提交失败")).when(journal).begin(any());var sent=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->interceptor.intercept(request(),"{}".getBytes(StandardCharsets.UTF_8),(r,b)->{sent.incrementAndGet();return new MockClientHttpResponse(new byte[0],HttpStatus.CREATED);}));
        assertEquals(0,sent.get());
    }
    @Test void shouldSendNothingWhenSendMarkerCommitFails() {
        doThrow(new IllegalStateException("模拟发送标记失败")).when(journal).sending(anyString(),isNull());var sent=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->interceptor.intercept(request(),new byte[0],(r,b)->{sent.incrementAndGet();return new MockClientHttpResponse(new byte[0],HttpStatus.CREATED);}));
        assertEquals(0,sent.get());
    }
    @Test void shouldKeepLostResponseUnknownAndNeverResend() {
        var sent=new AtomicInteger();
        assertThrows(IOException.class,()->interceptor.intercept(request(),new byte[0],(r,b)->{sent.incrementAndGet();throw new IOException("含敏感信息的远端错误");}));
        assertEquals(1,sent.get());
        verify(journal).finish(anyString(),isNull(),eq(GitWriteResult.Outcome.UNKNOWN),eq("{}"),eq("RESPONSE_UNCONFIRMED"));
    }
    @Test void shouldCorrelateRequestWithoutPersistingTokenOrBodyContent() throws Exception {
        var request=request();request.getHeaders().set("PRIVATE-TOKEN","sensitive-fixture");
        byte[] input="{\"source_branch\":\"feature\",\"target_branch\":\"main\",\"title\":\"private title\"}".getBytes(StandardCharsets.UTF_8);
        byte[] created = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                "iid", 17, "project_id", 17, "source_project_id", 17, "target_project_id", 17,
                "source_branch", "feature", "target_branch", "main", "sha", "a".repeat(40),
                "state", "opened", "web_url", "https://gitlab.invalid/acme/repo/-/merge_requests/17"));
        try(var response=interceptor.intercept(request,input,(r,b)->{
            assertTrue(new String(b,StandardCharsets.UTF_8).contains("ReleaseHub-Operation:"));
            assertNull(r.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID));
            assertNull(r.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_SHA));
            return new MockClientHttpResponse(created,HttpStatus.CREATED);
        })) { assertEquals(201,response.getStatusCode().value()); }
        var captor=org.mockito.ArgumentCaptor.forClass(GitOperationJournalPort.Command.class);verify(journal).begin(captor.capture());
        assertFalse(captor.getValue().toString().contains("sensitive-fixture"));
        assertFalse(captor.getValue().toString().contains("private title"));
        assertEquals("a".repeat(40),new ObjectMapper().readTree(captor.getValue().targetJson()).path("sha").asText());
        var evidence=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(journal).finish(anyString(),isNull(),eq(GitWriteResult.Outcome.APPLIED),evidence.capture(),isNull());
        assertEquals(17,new ObjectMapper().readTree(evidence.getValue()).path("iid").asInt());
        assertFalse(evidence.getValue().contains("sensitive-fixture"));
        assertFalse(evidence.getValue().contains("private title"));
    }
    @Test void shouldKeepMissingRemoteIdUnknown() {
        assertThrows(IOException.class,()->interceptor.intercept(request(),new byte[0],(r,b)->new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8),HttpStatus.CREATED)));
        verify(journal).finish(anyString(),isNull(),eq(GitWriteResult.Outcome.UNKNOWN),anyString(),eq("RESPONSE_UNCONFIRMED"));
    }
    @Test void shouldKeepMismatchedBranchEvidenceUnknown() throws Exception {
        var endpoint=new GitRemoteIdentityResolver.Endpoint("GITLAB","https://gitlab.invalid","acme/repo","https://gitlab.invalid/api/v4/projects/17","/repository/branches");
        when(identities.resolve(any(),any())).thenReturn(new GitRemoteIdentityResolver.Identity("repo","mapping","a".repeat(64),"GITLAB","https://gitlab.invalid","17",endpoint));
        when(identities.metadata(any(),anyString(),isNull(),any())).thenReturn(new ObjectMapper().readTree("{\"id\":\""+"a".repeat(40)+"\"}"));
        var request=new MockClientHttpRequest(HttpMethod.POST,URI.create(endpoint.projectEndpoint()+endpoint.suffix()));
        byte[] input="{\"branch\":\"feature\",\"ref\":\"main\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->interceptor.intercept(request,input,(r,b)->new MockClientHttpResponse(
                ("{\"name\":\"feature\",\"commit\":{\"id\":\""+"b".repeat(40)+"\"}}").getBytes(StandardCharsets.UTF_8),HttpStatus.CREATED)));
        verify(journal).finish(anyString(),isNull(),eq(GitWriteResult.Outcome.UNKNOWN),anyString(),eq("RESPONSE_UNCONFIRMED"));
    }
    @Test void shouldRejectFileChangedAfterContentWasRead() throws Exception {
        String originalRevision = "a".repeat(40);
        String latestRevision = "b".repeat(40);
        var remoteContent = new java.util.concurrent.atomic.AtomicReference<>("version=1.0.0\nexternal=B\n");
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid",
                "acme/repo", "https://gitlab.invalid/api/v4/projects/17", "/repository/files/gradle.properties");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        when(identities.metadata(any(), anyString(), eq("main"), any())).thenAnswer(invocation ->
                new ObjectMapper().createObjectNode().put("last_commit_id", latestRevision)
                        .put("content_sha256", GitRemoteIdentityResolver.hash(remoteContent.get())));
        var request = new MockClientHttpRequest(HttpMethod.PUT, URI.create(endpoint.projectEndpoint() + endpoint.suffix()));
        byte[] body = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                "branch", "main", "content", "version=2.0.0\n", "commit_message", "update",
                "last_commit_id", originalRevision));
        var sent = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> interceptor.intercept(request, body, (r, bytes) -> {
            sent.incrementAndGet();
            remoteContent.set(new ObjectMapper().readTree(bytes).path("content").asText());
            return new MockClientHttpResponse("{\"file_path\":\"gradle.properties\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        }));
        assertEquals(0, sent.get());
        assertEquals("version=1.0.0\nexternal=B\n", remoteContent.get());
        verify(journal, never()).begin(any());
    }

    @Test void shouldRejectFileWriteWithoutReadRevision() throws Exception {
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid",
                "acme/repo", "https://gitlab.invalid/api/v4/projects/17", "/repository/files/pom.xml");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        when(identities.metadata(any(), anyString(), eq("main"), any())).thenReturn(
                new ObjectMapper().createObjectNode().put("last_commit_id", "b".repeat(40))
                        .put("content_sha256", GitRemoteIdentityResolver.hash("new content")));
        var request = new MockClientHttpRequest(HttpMethod.PUT, URI.create(endpoint.projectEndpoint() + endpoint.suffix()));
        byte[] body = new ObjectMapper().writeValueAsBytes(java.util.Map.of(
                "branch", "main", "content", "new content", "commit_message", "update"));
        var sent = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> interceptor.intercept(request, body, (r, bytes) -> {
            sent.incrementAndGet();
            return new MockClientHttpResponse("{\"file_path\":\"pom.xml\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        }));
        assertEquals(0, sent.get());
        verify(journal, never()).begin(any());
    }

    @ParameterizedTest
    @CsvSource({
            "422,Merge request needs approval,APPROVAL_REQUIRED",
            "422,Pipeline is still running,PIPELINE_PENDING",
            "422,Pipeline failed,PIPELINE_FAILED",
            "403,Cannot merge into protected branch,PROTECTED_BRANCH",
            "401,Not authorized,PERMISSION_DENIED",
            "400,Merge conflict,CONFLICT",
            "400,No commits between source and target,NO_COMMITS",
            "422,Unexpected gate,HTTP_422"
    })
    void shouldPreserveCanonicalMrRejectionAcrossRealInterceptor(int status, String message, String expectedReason) throws Exception {
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.invalid", "acme/repo",
                "https://gitlab.invalid/api/v4/projects/17", "/merge_requests/7/merge");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "a".repeat(64), "GITLAB", "https://gitlab.invalid", "17", endpoint));
        var observedBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var http = new org.springframework.web.client.RestTemplate();
        http.getInterceptors().add((request, body, execution) -> {
            try (var response = execution.execute(request, body)) {
                byte[] bytes = response.getBody().readAllBytes();
                observedBody.set(new String(bytes, StandardCharsets.UTF_8));
                var copy = new MockClientHttpResponse(bytes, response.getStatusCode());
                copy.getHeaders().putAll(response.getHeaders());
                return copy;
            }
        });
        http.getInterceptors().add(interceptor);
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build();
        String rawBody = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "message", message + " sensitive-response-fixture", "debug", "private-debug-fixture"));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        endpoint.projectEndpoint() + endpoint.suffix()))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.PUT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(rawBody));

        var adapter = new GitLabMergeRequestAdapter(http,
                new GitRemoteIdentityResolver.Identity("repo", "mapping", "a".repeat(64), "GITLAB",
                        "https://gitlab.invalid", "17", endpoint), "sensitive-token-fixture");
        var result = adapter.merge("7", "a".repeat(40), "merge");

        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals(expectedReason, result.reason());
        var sanitized = new ObjectMapper().readTree(observedBody.get());
        assertEquals("REMOTE_WRITE_REJECTED", sanitized.path("message").asText());
        assertEquals(expectedReason, sanitized.path("reason").asText());
        assertEquals(2, sanitized.size());
        assertFalse(observedBody.get().contains("sensitive"));
        assertFalse(observedBody.get().contains("private-debug"));
        var evidence = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(journal).finish(anyString(),isNull(), eq(GitWriteResult.Outcome.REJECTED), evidence.capture(), eq(expectedReason));
        assertEquals(expectedReason, new ObjectMapper().readTree(evidence.getValue()).path("rejectionReason").asText());
        assertFalse(evidence.getValue().contains("sensitive"));
        assertFalse(evidence.getValue().contains("private-debug"));
        verify(journal, times(1)).sending(anyString(),isNull());
        server.verify();
    }

    @Test void shouldKeepUnconfirmedMergeStateUnknown() {
        var endpoint=new GitRemoteIdentityResolver.Endpoint("GITLAB","https://gitlab.invalid","acme/repo","https://gitlab.invalid/api/v4/projects/17","/merge_requests/7/merge");
        when(identities.resolve(any(),any())).thenReturn(new GitRemoteIdentityResolver.Identity("repo","mapping","a".repeat(64),"GITLAB","https://gitlab.invalid","17",endpoint));
        var request=new MockClientHttpRequest(HttpMethod.PUT,URI.create(endpoint.projectEndpoint()+endpoint.suffix()));
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID,"17");
        request.getHeaders().set(GitRemoteIdentityResolver.EXPECTED_SHA,"a".repeat(40));
        byte[] body=mergeIntentBody("a".repeat(40),"Merge feature/IT-1 into release/RW-1")
                .toString().getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->interceptor.intercept(request,body,(r,b)->new MockClientHttpResponse(
                "{\"iid\":7,\"state\":\"opened\"}".getBytes(StandardCharsets.UTF_8),HttpStatus.OK)));
        verify(journal).finish(anyString(),isNull(),eq(GitWriteResult.Outcome.UNKNOWN),anyString(),eq("RESPONSE_UNCONFIRMED"));
    }
}
