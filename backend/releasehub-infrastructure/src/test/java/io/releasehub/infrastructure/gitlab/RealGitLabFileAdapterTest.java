package io.releasehub.infrastructure.gitlab;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.settings.SettingsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest(httpPort = 0)
class RealGitLabFileAdapterTest {

    private RealGitLabFileAdapter adapter;
    private SettingsPort settingsPort;

    /** URI.create() 绕过 RestTemplate 二次编码，WireMock 接收到的路径为单次编码 %2F */
    private static final String ENC = "/api/v4/projects/acme%2Freleasehub";

    @BeforeEach
    void setUp() {
        settingsPort = mock(SettingsPort.class);
        when(settingsPort.getGitLab()).thenReturn(Optional.of(
                new SettingsPort.SettingsGitLab("http://localhost:0", "test-token")));

        adapter = new RealGitLabFileAdapter(settingsPort);
    }

    private void configureBaseUrl(WireMockRuntimeInfo wm) {
        when(settingsPort.getGitLab()).thenReturn(Optional.of(
                new SettingsPort.SettingsGitLab("http://localhost:" + wm.getHttpPort(), "test-token")));
        adapter = new RealGitLabFileAdapter(settingsPort);
    }

    @Test
    void shouldReadAndDecodeBase64File(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);
        String rawContent = "<?xml version=\"1.0\"?><project><version>1.2.3</version></project>";
        String base64Content = Base64.getEncoder().encodeToString(rawContent.getBytes());

        stubFor(get(urlPathEqualTo(ENC + "/repository/files/pom.xml"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("""
                                {"file_name":"pom.xml","file_path":"pom.xml","content":"%s","encoding":"base64"}
                                """.formatted(base64Content))));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "pom.xml");

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("<version>1.2.3</version>"));
    }

    @Test
    void shouldReturnEmptyWhenFileNotFound(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);

        stubFor(get(urlPathEqualTo(ENC + "/repository/files/missing.xml"))
                .willReturn(aResponse().withStatus(404)));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "missing.xml");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPermissionDenied(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);

        stubFor(get(urlPathEqualTo(ENC + "/repository/files/secret.json"))
                .willReturn(aResponse().withStatus(403)));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "secret.json");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReadGradlePropertiesFile(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);
        String rawContent = "version=2.0.0-SNAPSHOT\norg.gradle.jvmargs=-Xmx2048m";
        String base64Content = Base64.getEncoder().encodeToString(rawContent.getBytes());

        stubFor(get(urlPathEqualTo(ENC + "/repository/files/gradle.properties"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("""
                                {"file_name":"gradle.properties","file_path":"gradle.properties","content":"%s","encoding":"base64"}
                                """.formatted(base64Content))));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "develop", "gradle.properties");

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("version=2.0.0-SNAPSHOT"));
    }

    @Test
    void shouldDetectFileExists(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);

        stubFor(head(urlPathEqualTo(ENC + "/repository/files/pom.xml"))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(adapter.fileExists(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "pom.xml"));
    }

    @Test
    void shouldDetectFileNotExists(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);

        stubFor(head(urlPathEqualTo(ENC + "/repository/files/nonexistent.txt"))
                .willReturn(aResponse().withStatus(404)));

        assertFalse(adapter.fileExists(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "nonexistent.txt"));
    }

    @Test
    void shouldParseHTTPSCloneUrlWithDotGit(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);
        String rawContent = "version=1.0.0";
        String base64Content = Base64.getEncoder().encodeToString(rawContent.getBytes());

        // HTTPS with .git suffix: https://host/acme/releasehub.git → encode → acme%2Freleasehub
        stubFor(get(urlPathEqualTo(ENC + "/repository/files/gradle.properties"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("""
                                {"file_name":"gradle.properties","file_path":"gradle.properties","content":"%s","encoding":"base64"}
                                """.formatted(base64Content))));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "gradle.properties");

        assertTrue(result.isPresent());
    }

    @Test
    void shouldHandleEmptyBodyGracefully(WireMockRuntimeInfo wm) {
        configureBaseUrl(wm);

        stubFor(get(urlPathEqualTo(ENC + "/repository/files/pom.xml"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("null")));

        Optional<String> result = adapter.readFile(
                "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git",
                "main", "pom.xml");

        assertTrue(result.isEmpty());
    }
}
