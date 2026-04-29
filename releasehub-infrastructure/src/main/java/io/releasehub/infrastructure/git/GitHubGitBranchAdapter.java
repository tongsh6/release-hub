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
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitHubGitBranchAdapter implements GitBranchPort {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(GitProvider provider) {
        return provider == GitProvider.GITHUB;
    }

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String branchEndpoint = String.format("https://api.github.com/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(fromBranch));
            ResponseEntity<Map<String, Object>> branchResponse = restTemplate.exchange(branchEndpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            if (!branchResponse.getStatusCode().is2xxSuccessful() || branchResponse.getBody() == null) {
                return false;
            }
            Object commitObj = branchResponse.getBody().get("commit");
            if (!(commitObj instanceof Map<?, ?> commitMap) || commitMap.get("sha") == null) {
                return false;
            }
            String sha = String.valueOf(commitMap.get("sha"));
            String createRefEndpoint = String.format("https://api.github.com/repos/%s/%s/git/refs", ref.owner, ref.repo);
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
            String endpoint = String.format("https://api.github.com/repos/%s/%s/git/refs/heads/%s", ref.owner, ref.repo, urlEncode(branchName));
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
            String endpoint = String.format("https://api.github.com/repos/%s/%s/merges", ref.owner, ref.repo);
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
            String branchEndpoint = String.format("https://api.github.com/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(refName));
            ResponseEntity<Map<String, Object>> branchResponse = restTemplate.exchange(branchEndpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            if (!branchResponse.getStatusCode().is2xxSuccessful() || branchResponse.getBody() == null) {
                return false;
            }
            Object commitObj = branchResponse.getBody().get("commit");
            if (!(commitObj instanceof Map<?, ?> commitMap) || commitMap.get("sha") == null) {
                return false;
            }
            String sha = String.valueOf(commitMap.get("sha"));
            String createRefEndpoint = String.format("https://api.github.com/repos/%s/%s/git/refs", ref.owner, ref.repo);
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
            String prEndpoint = String.format("https://api.github.com/repos/%s/%s/pulls",
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
            String closeEndpoint = String.format("https://api.github.com/repos/%s/%s/pulls/%d",
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
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef ref = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("https://api.github.com/repos/%s/%s/branches/%s", ref.owner, ref.repo, urlEncode(branchName));
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
        Matcher ssh = Pattern.compile("^git@github.com:([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (ssh.find()) {
            return new RepoRef(ssh.group(1), ssh.group(2));
        }
        Matcher https = Pattern.compile("^https?://github.com/([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (https.find()) {
            return new RepoRef(https.group(1), https.group(2));
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record RepoRef(String owner, String repo) {
    }
}
