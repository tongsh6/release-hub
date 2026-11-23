package io.releasehub.infrastructure.gitlab;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest(httpPort = 0)
class GitLabAdapterTest {

    private SettingsPort settingsPort;
    private GitLabAdapter adapter;

    @BeforeEach
    void setUp() {
        settingsPort = mock(SettingsPort.class);
        adapter = new GitLabAdapter(settingsPort);
    }

    private void configureBaseUrl(WireMockRuntimeInfo wm, String token) {
        when(settingsPort.getGitLab()).thenReturn(Optional.of(
                new SettingsPort.SettingsGitLab("http://localhost:" + wm.getHttpPort(), token)));
    }

    // B13 independent contract: this legacy long consumer accepts only confirmed, representable project identities.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("unconfirmedProjectIdentityResponses")
    void projectIdentityRejectsUnconfirmedProjectResponse(String shape, int status, String body,
            WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.resolveProjectId(wm.getHttpBaseUrl() + "/acme/repo.git"), shape);

        assertEquals("GIT_OP_004", error.getCode(), shape);
        WireMock.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token")));
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> unconfirmedProjectIdentityResponses() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("STATUS_201", 201, "{\"id\":17}"),
                org.junit.jupiter.params.provider.Arguments.of("STATUS_206", 206, "{\"id\":17}"),
                org.junit.jupiter.params.provider.Arguments.of("STRING_ID", 200, "{\"id\":\"17\"}"),
                org.junit.jupiter.params.provider.Arguments.of("INTEGRAL_LOOKING_DECIMAL", 200, "{\"id\":17.0}"),
                org.junit.jupiter.params.provider.Arguments.of("FRACTIONAL_ID", 200, "{\"id\":17.5}"),
                org.junit.jupiter.params.provider.Arguments.of("ZERO_ID", 200, "{\"id\":0}"),
                org.junit.jupiter.params.provider.Arguments.of("NEGATIVE_ID", 200, "{\"id\":-1}"),
                org.junit.jupiter.params.provider.Arguments.of("MISSING_ID", 200, "{}"),
                org.junit.jupiter.params.provider.Arguments.of("NULL_ID", 200, "{\"id\":null}"),
                org.junit.jupiter.params.provider.Arguments.of("NON_OBJECT", 200, "[{\"id\":17}]"),
                org.junit.jupiter.params.provider.Arguments.of("EMPTY_BODY", 200, ""),
                org.junit.jupiter.params.provider.Arguments.of("LONG_OVERFLOW", 200, "{\"id\":9223372036854775808}"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {1L, Long.MAX_VALUE})
    void projectIdentityAcceptsPositiveLongBoundary(long expected, WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":" + expected + ",\"path_with_namespace\":\"acme/repo\"}")));

        assertEquals(expected, adapter.resolveProjectId(wm.getHttpBaseUrl() + "/acme/repo.git"));

        WireMock.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 403, 404})
    void projectIdentityPreservesHttpErrorPolicy(int status, WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token"))
                .willReturn(aResponse().withStatus(status)));

        var error = assertThrows(org.springframework.web.client.HttpClientErrorException.class,
                () -> adapter.resolveProjectId(wm.getHttpBaseUrl() + "/acme/repo.git"));

        assertEquals(status, error.getStatusCode().value());
        WireMock.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .withHeader("PRIVATE-TOKEN", WireMock.equalTo("valid-token")));
    }

    @Test
    void projectIdentityPreservesTransportFailurePolicy() {
        when(settingsPort.getGitLab()).thenReturn(Optional.of(
                new SettingsPort.SettingsGitLab("http://127.0.0.1:1", "valid-token")));

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> adapter.resolveProjectId("http://127.0.0.1:1/acme/repo.git"));
    }

    @Test
    void projectIdentityPreservesMissingSettingsPolicy() {
        when(settingsPort.getGitLab()).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.resolveProjectId("https://gitlab.invalid/acme/repo.git"));

        assertEquals("GITLAB_001", error.getCode());
        WireMock.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    // End B13 independent identity contract.

    @Test
    void shouldPreserveEncodedProjectAndBranchPaths(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/projects/acme%2Frepo"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":17}")));
        stubFor(get(urlPathEqualTo("/api/v4/projects/17/repository/branches/feature%2FS2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"feature/S2\"}")));

        assertEquals(17L, adapter.resolveProjectId("http://localhost:" + wm.getHttpPort() + "/acme/repo.git"));
        assertTrue(adapter.branchExists(17L, "feature/S2"));
    }

    @Test
    void shouldTestConnectionWithGitLabCurrentUserApi(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/user"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"id\":1,\"username\":\"root\"}")));

        assertTrue(adapter.testConnection());
    }

    @Test
    void shouldRejectConnectionWhenGitLabReturnsUnauthorized(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "invalid-token");
        stubFor(get(urlPathEqualTo("/api/v4/user"))
                .willReturn(aResponse().withStatus(401)));

        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.testConnection());

        assertEquals("GITLAB_004", ex.getCode());
        assertEquals(0, ex.getArgs().length);
    }

    @Test
    void shouldRejectConnectionWhenGitLabReturnsForbidden(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "insufficient-scope-token");
        stubFor(get(urlPathEqualTo("/api/v4/user"))
                .willReturn(aResponse().withStatus(403)));

        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.testConnection());

        assertEquals("GITLAB_005", ex.getCode());
        assertEquals(0, ex.getArgs().length);
    }

    @Test
    void shouldRejectConnectionWhenGitLabIsUnreachable() {
        when(settingsPort.getGitLab()).thenReturn(Optional.of(
                new SettingsPort.SettingsGitLab("http://127.0.0.1:1", "valid-token")));

        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.testConnection());

        assertEquals("GITLAB_006", ex.getCode());
        assertEquals(0, ex.getArgs().length);
    }

    @Test
    void shouldRejectConnectionWhenSettingsMissing() {
        when(settingsPort.getGitLab()).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.testConnection());

        assertEquals("GITLAB_001", ex.getCode());
    }

    @Test
    void shouldExcludeArchivedBranchesFromActiveAndNonCompliantCounts(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm, "valid-token");
        stubFor(get(urlPathEqualTo("/api/v4/projects/42/repository/branches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("""
                                [
                                  {"name":"main"},
                                  {"name":"feature/ITER-1"},
                                  {"name":"archive/unpublished/release-RW-1"},
                                  {"name":"legacy_branch"}
                                ]
                                """)));

        GitLabPort.BranchStatistics statistics = adapter.fetchBranchStatistics(42L);

        assertEquals(4, statistics.total());
        assertEquals(3, statistics.active());
        assertEquals(1, statistics.nonCompliant());
    }
}
