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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitLabGitBranchAdapterTest {

    private GitLabGitBranchAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        adapter = new GitLabGitBranchAdapter();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(adapter, "restTemplate");
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void shouldSupportGitLabProvider() {
        assertTrue(adapter.supports(GitProvider.GITLAB));
        assertFalse(adapter.supports(GitProvider.GITHUB));
    }

    @Test
    void shouldCreateBranchSuccessfully() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/repository/branches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PRIVATE-TOKEN", "token"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        boolean created = adapter.createBranch(
                "git@gitlab.example.com:acme/releasehub.git",
                "token",
                "release/RW-1",
                "main"
        );

        assertTrue(created);
        server.verify();
    }

    @Test
    void shouldMergeSuccessfully() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"iid\":101}"));

        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests/101/merge"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                "https://gitlab.example.com/acme/releasehub.git",
                "token",
                "feature/ITER-1",
                "release/RW-1",
                "merge"
        );

        assertEquals(MergeStatus.SUCCESS, result.status());
        server.verify();
    }

    @Test
    void shouldReturnConflictWhenMergeFailedWithConflictBody() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"cannot be merged due to conflict\"}"));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                "https://gitlab.example.com/acme/releasehub.git",
                "token",
                "feature/ITER-1",
                "release/RW-1",
                "merge"
        );

        assertEquals(MergeStatus.CONFLICT, result.status());
        assertTrue(result.detail().contains("conflict"));
        server.verify();
    }

    @Test
    void shouldGetBranchStatusSuccessfully() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/repository/branches/release%252FRW-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"commit\":{\"id\":\"abc123\"}}"));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                "https://gitlab.example.com/acme/releasehub.git",
                "token",
                "release/RW-1"
        );

        assertTrue(status.exists());
        assertEquals("abc123", status.latestCommit());
        server.verify();
    }

    @Test
    void shouldCheckMergeabilityAsMergeable() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"iid\":201,\"merge_status\":\"can_be_merged\"}"));

        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests/201"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                "https://gitlab.example.com/acme/releasehub.git",
                "token",
                "feature/ITER-1",
                "release/RW-1"
        );

        assertTrue(result.canMerge());
        server.verify();
    }

    @Test
    void shouldCheckMergeabilityAsConflict() {
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"iid\":202,\"merge_status\":\"cannot_be_merged\"}"));

        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/acme%252Freleasehub/merge_requests/202"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                "https://gitlab.example.com/acme/releasehub.git",
                "token",
                "feature/ITER-1",
                "release/RW-1"
        );

        assertFalse(result.canMerge());
        assertTrue(result.detail().contains("conflict"));
        server.verify();
    }
}
