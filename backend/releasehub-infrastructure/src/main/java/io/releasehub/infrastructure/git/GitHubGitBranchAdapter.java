package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.repo.GitProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitHubGitBranchAdapter implements GitBranchPort {

    private RestTemplate restTemplate;

    public GitHubGitBranchAdapter(RestTemplateBuilder builder) {
        this.restTemplate = GitHttpClients.create(builder);
    }

    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Resolves the GitHub API base URL from the clone URL host.
     *
     * <p>Public GitHub ({@code github.com}) maps to {@code https://api.github.com}.
     * GitHub Enterprise instances use {@code https://<host>/api/v3}.
     */
    private String resolveApiBaseUrl(String scheme, String host) {
        if ("github.com".equals(host)) {
            return "https://api.github.com";
        }
        return scheme + "://" + host;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void installWriteSafety(io.releasehub.infrastructure.git.GitWriteHttpInterceptor interceptor) {
        restTemplate.getInterceptors().add(interceptor);
    }

    @Override
    public boolean supports(GitProvider provider) {
        return provider == GitProvider.GITHUB;
    }

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String branchEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(fromBranch));
            ResponseEntity<Map<String, Object>> branchResponse = restTemplate.exchange(branchEndpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            Map<String, Object> branchBody = branchResponse.getBody();
            if (!branchResponse.getStatusCode().is2xxSuccessful() || branchBody == null) {
                return false;
            }
            Object commitObj = branchBody.get("commit");
            if (!(commitObj instanceof Map<?, ?> commitMap) || commitMap.get("sha") == null) {
                return false;
            }
            String sha = String.valueOf(commitMap.get("sha"));
            String createRefEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/git/refs", ref.owner, ref.repo);
            Map<String, String> body = Map.of("ref", "refs/heads/" + branchName, "sha", sha);
            ResponseEntity<Map<String, Object>> created = restTemplate.exchange(createRefEndpoint, HttpMethod.POST, new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {});
            return created.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public boolean deleteBranch(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/git/refs/heads/%s", ref.owner, ref.repo, urlEncode(branchName));
            ResponseEntity<Void> response = restTemplate.exchange(endpoint, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/merges", ref.owner, ref.repo);
            Map<String, String> body = new HashMap<>();
            body.put("base", targetBranch);
            body.put("head", sourceBranch);
            body.put("commit_message", commitMessage);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.POST, new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {});
            if (response.getStatusCode().is2xxSuccessful()) {
                return MergeResult.success();
            }
            return MergeResult.failed("merge failed");
        } catch (HttpClientErrorException.Conflict e) {
            return MergeResult.conflict(e.getResponseBodyAsString());
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value() == 408 ? MergeResult.unknown("HTTP_408") : MergeResult.failed("HTTP_" + e.getStatusCode().value());
        } catch (org.springframework.web.client.RestClientException e) {
            return MergeResult.unknown("RESPONSE_UNCONFIRMED");
        }
    }

    @Override
    public GitWriteResult<MrEvidence> ensureMergeRequest(String repoCloneUrl, String token, String sourceBranch,
                                                        String targetBranch, String message) {
        return GitWriteResult.rejected("PROVIDER_UNSUPPORTED");
    }

    @Override
    public MergeResult tryMergeRequest(String repoCloneUrl, String token, MrEvidence checkpoint, String message) {
        return MergeResult.failed("PROVIDER_UNSUPPORTED");
    }

    @Override
    public GitReadResult<TagEvidence> readTag(String repoCloneUrl, String token, String expectedProjectId, String tagName) {
        return new GitReadResult<>(GitReadResult.Status.UNSUPPORTED, null);
    }

    @Override
    public GitWriteResult<TagEvidence> createTag(String repoCloneUrl, String token, String expectedProjectId,
                                                String tagName, String targetSha, String message) {
        return GitWriteResult.rejected("PROVIDER_UNSUPPORTED");
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        RepoRef ref = parseRepoRef(repoCloneUrl);
        var client = new GitHubMergeRequestAdapter(restTemplate,
                ref.apiBaseUrl + "/repos/" + ref.owner + "/" + ref.repo + "/pulls", token);
        return new io.releasehub.application.gitoperation.GitMergeWorkflow(client, 8, () -> {
            try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }).probe(sourceBranch, targetBranch);
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String shaEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/git/refs/heads/%s",
                    ref.owner, ref.repo, urlEncode(branchName));
            ResponseEntity<Map<String, Object>> refResponse = restTemplate.exchange(
                    shaEndpoint, HttpMethod.GET,
                    new HttpEntity<>(headers(token)),
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> refBody = refResponse.getBody();
            if (!refResponse.getStatusCode().is2xxSuccessful() || refBody == null) {
                return false;
            }
            Object objectObj = refBody.get("object");
            if (!(objectObj instanceof Map<?, ?> objMap) || objMap.get("sha") == null) {
                return false;
            }
            String sha = String.valueOf(objMap.get("sha"));
            String archivedName = "archive/" + reason + "/" + branchName.replace("/", "-");
            String createRefEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/git/refs",
                    ref.owner, ref.repo);
            Map<String, String> body = Map.of("ref", "refs/heads/" + archivedName, "sha", sha);
            restTemplate.exchange(createRefEndpoint, HttpMethod.POST,
                    new HttpEntity<>(body, headers(token)),
                    new ParameterizedTypeReference<>() {});
            return deleteBranch(repoCloneUrl, token, branchName);
        } catch (HttpClientErrorException e) {
            log.warn("Error archiving branch '{}': {}", branchName, e.getMessage());
            return false;
        }
    }

    @Override
    public GitWriteResult<PipelineEvidence> triggerPipeline(String repoCloneUrl, String token, String expectedProjectId,
                                                            String ref, String expectedSha) {
        return GitWriteResult.rejected("PROVIDER_UNSUPPORTED");
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(branchName));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            Map<String, Object> responseBody = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
                return BranchStatus.missing(null);
            }
            Object commitObj = responseBody.get("commit");
            String latestCommit = null;
            if (commitObj instanceof Map<?, ?> commitMap && commitMap.get("sha") != null) {
                latestCommit = String.valueOf(commitMap.get("sha"));
            }
            return BranchStatus.present(null, latestCommit);
        } catch (HttpClientErrorException.NotFound e) {
            return BranchStatus.missing(null);
        }
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }

    private RepoRef parseRepoRef(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw ValidationException.invalidParameter("cloneUrl");
        }
        String trimmed = cloneUrl.trim();
        Matcher ssh = Pattern.compile("^git@([^:]+):([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (ssh.find()) {
            String host = ssh.group(1);
            return new RepoRef(resolveApiBaseUrl("https", host), ssh.group(2), ssh.group(3));
        }
        Matcher https = Pattern.compile("^(https?)://([^/]+)/([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (https.find()) {
            String scheme = https.group(1);
            String host = https.group(2);
            return new RepoRef(resolveApiBaseUrl(scheme, host), https.group(3), https.group(4));
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }

    @Override
    public List<String> listBranches(String repoCloneUrl, String token, String prefix) {
        try {
            String branchPrefix = prefix == null ? "" : prefix;
            RepoRef rp = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(rp.apiBaseUrl + "/repos/%s/%s/branches?per_page=100",
                    rp.owner, rp.repo);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, new HttpEntity<>(headers(token)),
                    new ParameterizedTypeReference<>() {});
            List<Map<String, Object>> branches = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || branches == null) {
                return List.of();
            }
            return branches.stream()
                    .map(b -> b.get("name"))
                    .filter(name -> name != null)
                    .map(String::valueOf)
                    .filter(name -> branchPrefix.isBlank() || name.startsWith(branchPrefix))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to list branches for {}: {}", repoCloneUrl, e.getMessage());
            return List.of();
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record RepoRef(String apiBaseUrl, String owner, String repo) {
    }
}
