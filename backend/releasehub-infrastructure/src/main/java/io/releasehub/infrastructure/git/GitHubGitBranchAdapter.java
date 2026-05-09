package io.releasehub.infrastructure.git;

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
        this.restTemplate = builder.build();
    }

    /** Exposed for testability — allows WireMock-backed RestTemplate injection. */
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
            if (!branchResponse.getStatusCode().is2xxSuccessful() || branchResponse.getBody() == null) {
                return false;
            }
            Object commitObj = branchResponse.getBody().get("commit");
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
            return MergeResult.failed(e.getResponseBodyAsString());
        }
    }

    @Override
    public boolean createTag(String repoCloneUrl, String token, String tagName, String refName, String message) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String branchEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(refName));
            ResponseEntity<Map<String, Object>> branchResponse = restTemplate.exchange(branchEndpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            if (!branchResponse.getStatusCode().is2xxSuccessful() || branchResponse.getBody() == null) {
                return false;
            }
            Object commitObj = branchResponse.getBody().get("commit");
            if (!(commitObj instanceof Map<?, ?> commitMap) || commitMap.get("sha") == null) {
                return false;
            }
            String sha = String.valueOf(commitMap.get("sha"));
            String createRefEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/git/refs", ref.owner, ref.repo);
            Map<String, String> body = Map.of("ref", "refs/tags/" + tagName, "sha", sha);
            ResponseEntity<Map<String, Object>> created = restTemplate.exchange(createRefEndpoint, HttpMethod.POST, new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {});
            return created.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);

            // Create a temporary PR to check mergeability
            String prEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/pulls",
                    ref.owner, ref.repo);
            Map<String, String> prBody = new HashMap<>();
            prBody.put("title", "[ReleaseHub] merge check: " + sourceBranch + " → " + targetBranch);
            prBody.put("head", sourceBranch);
            prBody.put("base", targetBranch);

            ResponseEntity<Map<String, Object>> prResponse = restTemplate.exchange(
                    prEndpoint, HttpMethod.POST,
                    new HttpEntity<>(prBody, headers(token)),
                    new ParameterizedTypeReference<>() {});

            if (!prResponse.getStatusCode().is2xxSuccessful() || prResponse.getBody() == null) {
                return MergeabilityResult.error("failed to create pull request");
            }

            Map<String, Object> pr = prResponse.getBody();
            int number = ((Number) pr.get("number")).intValue();

            // Close the temporary PR
            String closeEndpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/pulls/%d",
                    ref.owner, ref.repo, number);
            Map<String, String> closeBody = Map.of("state", "closed");
            restTemplate.exchange(closeEndpoint, HttpMethod.PATCH,
                    new HttpEntity<>(closeBody, headers(token)),
                    new ParameterizedTypeReference<>() {});

            // Check mergeable field (may be null if GitHub hasn't computed it yet)
            Boolean mergeable = (Boolean) pr.get("mergeable");
            if (Boolean.FALSE.equals(mergeable)) {
                return MergeabilityResult.conflict("merge conflict detected");
            }
            return MergeabilityResult.mergeable();
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 422 || e.getStatusCode().value() == 404) {
                return MergeabilityResult.conflict("branch not found or no commits in common: " + body);
            }
            return MergeabilityResult.error(body);
        } catch (Exception e) {
            return MergeabilityResult.error(e.getMessage());
        }
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
            if (!refResponse.getStatusCode().is2xxSuccessful() || refResponse.getBody() == null) {
                return false;
            }
            Object objectObj = refResponse.getBody().get("object");
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
    public String triggerPipeline(String repoCloneUrl, String token, String ref) {
        try {
            RepoRef rp = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(rp.apiBaseUrl + "/repos/%s/%s/dispatches",
                    rp.owner, rp.repo);
            Map<String, Object> body = new HashMap<>();
            body.put("event_type", "releasehub-release");
            Map<String, String> clientPayload = new HashMap<>();
            clientPayload.put("ref", ref);
            body.put("client_payload", clientPayload);

            restTemplate.exchange(endpoint, HttpMethod.POST,
                    new HttpEntity<>(body, headers(token)),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            log.info("GitHub repository_dispatch triggered for ref '{}' on {}/{}", ref, rp.owner, rp.repo);
            return "dispatch:" + rp.owner + "/" + rp.repo + ":" + ref;
        } catch (HttpClientErrorException.NotFound e) {
            log.info("GitHub pipeline trigger not configured: no repository_dispatch receiver found. Add a workflow with repository_dispatch[releasehub-release] trigger.");
            return null;
        } catch (HttpClientErrorException e) {
            log.warn("Failed to trigger GitHub pipeline for ref '{}': {}", ref, e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Error triggering GitHub pipeline for ref '{}': {}", ref, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(ref.apiBaseUrl + "/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(branchName));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return BranchStatus.missing();
            }
            Object commitObj = response.getBody().get("commit");
            String latestCommit = null;
            if (commitObj instanceof Map<?, ?> commitMap && commitMap.get("sha") != null) {
                latestCommit = String.valueOf(commitMap.get("sha"));
            }
            return BranchStatus.present(latestCommit);
        } catch (HttpClientErrorException.NotFound e) {
            return BranchStatus.missing();
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
            RepoRef rp = parseRepoRef(repoCloneUrl);
            String endpoint = String.format(rp.apiBaseUrl + "/repos/%s/%s/branches?per_page=100",
                    rp.owner, rp.repo);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, new HttpEntity<>(headers(token)),
                    new ParameterizedTypeReference<>() {});
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            return response.getBody().stream()
                    .map(b -> String.valueOf(b.get("name")))
                    .filter(name -> name.startsWith(prefix))
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
