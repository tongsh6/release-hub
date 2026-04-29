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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitLabGitBranchAdapter implements GitBranchPort {

    private final RestTemplate restTemplate = new RestTemplate();

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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
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
            ResponseEntity<Void> response = restTemplate.exchange(endpoint, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
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
            ResponseEntity<Map<String, Object>> created = restTemplate.exchange(createMrEndpoint, HttpMethod.POST, new HttpEntity<>(mrBody, headers(token)), new ParameterizedTypeReference<>() {});
            if (!created.getStatusCode().is2xxSuccessful() || created.getBody() == null || created.getBody().get("iid") == null) {
                return MergeResult.failed("failed to create merge request");
            }
            int iid = toInt(created.getBody().get("iid"));
            String mergeEndpoint = String.format("%s/api/v4/projects/%s/merge_requests/%d/merge", repoRef.baseUrl, repoRef.encodedPath, iid);
            ResponseEntity<Map<String, Object>> merged = restTemplate.exchange(mergeEndpoint, HttpMethod.PUT, new HttpEntity<>(Map.of("merge_commit_message", commitMessage), headers(token)), new ParameterizedTypeReference<>() {});
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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.POST, new HttpEntity<>(body, headers(token)), new ParameterizedTypeReference<>() {});
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
                    mrEndpoint, HttpMethod.POST,
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
            restTemplate.exchange(closeEndpoint, HttpMethod.PUT,
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
    public BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches/%s", repoRef.baseUrl, repoRef.encodedPath, urlEncode(branchName));
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {});
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
        Matcher https = Pattern.compile("^https?://([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (https.find()) {
            String baseUrl = "https://" + https.group(1);
            String path = https.group(2);
            return new RepoRef(baseUrl, urlEncode(path));
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
