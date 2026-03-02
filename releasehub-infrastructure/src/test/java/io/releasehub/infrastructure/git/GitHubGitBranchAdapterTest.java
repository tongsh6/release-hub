package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitHubGitBranchAdapterTest {

    private GitHubGitBranchAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        adapter = new GitHubGitBranchAdapter();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(adapter, "restTemplate");
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void shouldSupportGitHubProvider() {
        assertTrue(adapter.supports(GitProvider.GITHUB));
        assertFalse(adapter.supports(GitProvider.GITLAB));
    }

    @Test
    void shouldCreateBranchSuccessfully() {
        server.expect(requestTo("https://api.github.com/repos/acme/releasehub/branches/main"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{" +
                                "\"commit\":{\"sha\":\"abc123\"}" +
                                "}"));

        server.expect(requestTo("https://api.github.com/repos/acme/releasehub/git/refs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        boolean created = adapter.createBranch(
                "https://github.com/acme/releasehub.git",
                "token",
                "release/RW-1",
                "main"
        );

        assertTrue(created);
        server.verify();
    }

    @Test
    void shouldReturnConflictWhenMergeHasConflict() {
        server.expect(requestTo("https://api.github.com/repos/acme/releasehub/merges"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"merge conflict\"}"));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                "https://github.com/acme/releasehub.git",
                "token",
                "feature/ITER-1",
                "release/RW-1",
                "merge"
        );

        assertEquals(MergeStatus.CONFLICT, result.status());
        assertTrue(result.detail().contains("merge conflict"));
        server.verify();
    }

    @Test
    void shouldReturnMissingWhenBranchNotFound() {
        server.expect(requestTo("https://api.github.com/repos/acme/releasehub/branches/release%252FRW-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                "https://github.com/acme/releasehub.git",
                "token",
                "release/RW-1"
        );

        assertFalse(status.exists());
        assertNull(status.latestCommit());
        server.verify();
    }
}
