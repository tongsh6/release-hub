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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "releasehub.gitlab.real-adapter", havingValue = "true")
public class GitLabGitBranchAdapter implements GitBranchPort {

    private RestTemplate restTemplate;

    public GitLabGitBranchAdapter(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /** Exposed for testability — allows WireMock-backed RestTemplate injection. */
    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(GitProvider provider) {
        return provider == GitProvider.GITLAB;
    }

    @Override
    public boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches", repoRef.baseUrl, repoRef.encodedPath);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("branch", branchName, "ref", fromBranch), headers(token));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri(endpoint), HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public boolean deleteBranch(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches/%s", repoRef.baseUrl, repoRef.encodedPath, urlEncode(branchName));
            ResponseEntity<Void> response = restTemplate.exchange(uri(endpoint), HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String createMrEndpoint = String.format("%s/api/v4/projects/%s/merge_requests", repoRef.baseUrl, repoRef.encodedPath);
            Map<String, Object> mrBody = new HashMap<>();
            mrBody.put("source_branch", sourceBranch);
            mrBody.put("target_branch", targetBranch);
            mrBody.put("title", commitMessage);
            mrBody.put("remove_source_branch", false);
            ResponseEntity<Map<String, Object>> created = restTemplate.exchange(uri(createMrEndpoint), HttpMethod.POST, new HttpEntity<>(mrBody, headers(token)), new ParameterizedTypeReference<>() {});
            if (!created.getStatusCode().is2xxSuccessful() || created.getBody() == null || created.getBody().get("iid") == null) {
                return MergeResult.failed("failed to create merge request");
            }
            int iid = toInt(created.getBody().get("iid"));
            String mergeEndpoint = String.format("%s/api/v4/projects/%s/merge_requests/%d/merge", repoRef.baseUrl, repoRef.encodedPath, iid);
            ResponseEntity<Map<String, Object>> merged = restTemplate.exchange(uri(mergeEndpoint), HttpMethod.PUT, new HttpEntity<>(Map.of("merge_commit_message", commitMessage), headers(token)), new ParameterizedTypeReference<>() {});
            if (merged.getStatusCode().is2xxSuccessful()) {
                return MergeResult.success();
            }
            return MergeResult.failed("merge failed");
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body != null && (body.contains("conflict") || body.contains("cannot be merged"))) {
                return MergeResult.conflict(body);
            }
            return MergeResult.failed(body);
        } catch (Exception e) {
            return MergeResult.failed(e.getMessage());
        }
    }

    @Override
    public boolean createTag(String repoCloneUrl, String token, String tagName, String ref, String message) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/tags", repoRef.baseUrl, repoRef.encodedPath);
            Map<String, String> body = new HashMap<>();
            body.put("tag_name", tagName);
            body.put("ref", ref);
            body.put("message", message == null ? "" : message);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri(endpoint), HttpMethod.POST, new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {});
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);

            // Create a temporary MR to check merge_status
            String mrEndpoint = String.format("%s/api/v4/projects/%s/merge_requests", repoRef.baseUrl, repoRef.encodedPath);
            Map<String, Object> mrBody = new HashMap<>();
            mrBody.put("source_branch", sourceBranch);
            mrBody.put("target_branch", targetBranch);
            mrBody.put("title", "[ReleaseHub] merge check: " + sourceBranch + " → " + targetBranch);
            mrBody.put("remove_source_branch", false);

            ResponseEntity<Map<String, Object>> mrResponse = restTemplate.exchange(
                    uri(mrEndpoint), HttpMethod.POST,
                    new HttpEntity<>(mrBody, headers(token)),
                    new ParameterizedTypeReference<>() {});

            if (!mrResponse.getStatusCode().is2xxSuccessful() || mrResponse.getBody() == null) {
                return MergeabilityResult.error("failed to create merge request");
            }

            Map<String, Object> mr = mrResponse.getBody();
            int iid = toInt(mr.get("iid"));
            String mergeStatus = String.valueOf(mr.getOrDefault("merge_status", ""));

            // Close the temporary MR
            String closeEndpoint = String.format("%s/api/v4/projects/%s/merge_requests/%d",
                    repoRef.baseUrl, repoRef.encodedPath, iid);
            Map<String, String> closeBody = Map.of("state_event", "close");
            restTemplate.exchange(uri(closeEndpoint), HttpMethod.PUT,
                    new HttpEntity<>(closeBody, headers(token)),
                    new ParameterizedTypeReference<>() {});

            if ("cannot_be_merged".equals(mergeStatus)) {
                return MergeabilityResult.conflict("merge conflict detected");
            }
            return MergeabilityResult.mergeable();
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                return MergeabilityResult.conflict("branch not found or invalid: " + body);
            }
            if (e.getStatusCode().value() == 409) {
                return MergeabilityResult.conflict("merge conflict: " + body);
            }
            return MergeabilityResult.error(body);
        } catch (Exception e) {
            return MergeabilityResult.error(e.getMessage());
        }
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        try {
            String archivedBranchName = "archive/" + reason + "/" + branchName.replace("/", "-");
            boolean created = createBranch(repoCloneUrl, token, archivedBranchName, branchName);
            if (!created) {
                log.warn("Failed to create archive branch '{}', proceeding to delete original", archivedBranchName);
            }
            return deleteBranch(repoCloneUrl, token, branchName);
        } catch (Exception e) {
            log.warn("Error archiving branch '{}': {}", branchName, e.getMessage());
            return false;
        }
    }

    @Override
    public String triggerPipeline(String repoCloneUrl, String token, String ref) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/pipeline?ref=%s",
                    repoRef.baseUrl, repoRef.encodedPath, urlEncode(ref));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri(endpoint), HttpMethod.POST,
                    new HttpEntity<>(headers(token)),
                    new ParameterizedTypeReference<>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                return id != null ? String.valueOf(id) : null;
            }
            return null;
        } catch (HttpClientErrorException e) {
            log.warn("Failed to trigger pipeline for ref '{}': {}", ref, e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Error triggering pipeline for ref '{}': {}", ref, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<String> listBranches(String repoCloneUrl, String token, String prefix) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches?search=%s&per_page=100",
                    repoRef.baseUrl, repoRef.encodedPath, urlEncode(prefix));
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri(endpoint), HttpMethod.GET, new HttpEntity<>(headers(token)),
                    new ParameterizedTypeReference<>() {});
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            return response.getBody().stream()
                    .map(b -> String.valueOf(b.get("name")))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to list branches for {} with prefix {}: {}", repoCloneUrl, prefix, e.getMessage());
            return List.of();
        }
    }

    @Override
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches/%s", repoRef.baseUrl, repoRef.encodedPath, urlEncode(branchName));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri(endpoint), HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return BranchStatus.missing();
            }
            Object commitObj = response.getBody().get("commit");
            String latestCommit = null;
            if (commitObj instanceof Map<?, ?> commitMap) {
                Object id = commitMap.get("id");
                latestCommit = id == null ? null : String.valueOf(id);
            }
            return BranchStatus.present(latestCommit);
        } catch (HttpClientErrorException.NotFound e) {
            return BranchStatus.missing();
        }
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }

    private RepoRef parseRepoRef(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw ValidationException.invalidParameter("cloneUrl");
        }
        String trimmed = cloneUrl.trim();
        Matcher ssh = Pattern.compile("^git@([^:]+):(.+?)(\\.git)?$").matcher(trimmed);
        if (ssh.find()) {
            String baseUrl = "https://" + ssh.group(1);
            String path = ssh.group(2);
            return new RepoRef(baseUrl, urlEncode(path));
        }
        Matcher https = Pattern.compile("^(https?)://([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (https.find()) {
            String baseUrl = https.group(1) + "://" + https.group(2);
            String path = https.group(3);
            return new RepoRef(baseUrl, urlEncode(path));
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 把 endpoint 字符串包装成 URI，避免 RestTemplate 对已编码字符串再次 encode。
     *
     * <p>背景：RestTemplate.exchange(String url, ...) 会用 UriTemplate 解析 url，把 {@code %2F}
     * 当成字面 % + 2 + F → 再次编码为 {@code %252F}，导致 GitLab API 收到的 path 是
     * {@code e2e-user%252Fseed-repo} 而非期望的 {@code e2e-user%2Fseed-repo}，
     * 返回 401/404。传入 URI 实例可绕过 UriTemplate 二次编码。
     */
    private URI uri(String endpoint) {
        return URI.create(endpoint);
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private record RepoRef(String baseUrl, String encodedPath) {
    }
}
