package io.releasehub.infrastructure.gitlab;

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

        assertEquals("GITLAB_003", ex.getCode());
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
