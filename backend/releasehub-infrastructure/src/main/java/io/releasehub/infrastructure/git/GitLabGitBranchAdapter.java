package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
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
import org.springframework.web.client.RestClientException;
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

    private static final int MERGE_READINESS_MAX_ATTEMPTS = 80;
    private static final long MERGE_READINESS_POLL_MILLIS = 250L;

    private RestTemplate restTemplate;

    public GitLabGitBranchAdapter(RestTemplateBuilder builder) {
        this.restTemplate = GitHttpClients.create(builder);
    }

    void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void installWriteSafety(io.releasehub.infrastructure.git.GitWriteHttpInterceptor interceptor) {
        restTemplate.getInterceptors().add(interceptor);
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
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            confirmedRefSha(response, branchName, ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 408) throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return false;
        } catch (RestClientException e) {
            throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
        }
    }

    @Override
    public boolean deleteBranch(String repoCloneUrl, String token, String branchName) {
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = String.format("%s/api/v4/projects/%s/repository/branches/%s", repoRef.baseUrl, repoRef.encodedPath, urlEncode(branchName));
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
            if (response.getStatusCode().value() != 204) throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 408) throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return false;
        } catch (RestClientException e) {
            throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
        }
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage) {
        return mergeWorkflow(repoCloneUrl, token).merge(sourceBranch, targetBranch, commitMessage);
    }

    private String confirmedRefSha(ResponseEntity<Map<String, Object>> response, String expectedName, ErrorCode failureCode) {
        var body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || !expectedName.equals(body.get("name"))
                || !(body.get("commit") instanceof Map<?, ?> commit) || !(commit.get("id") instanceof String sha)
                || !sha.matches("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")) {
            throw BusinessException.of(failureCode);
        }
        return sha;
    }

    private io.releasehub.application.gitoperation.GitMergeWorkflow mergeWorkflow(String cloneUrl, String token) {
        RepoRef ref = parseRepoRef(cloneUrl);
        var client = new GitLabMergeRequestAdapter(restTemplate,
                ref.baseUrl + "/api/v4/projects/" + ref.encodedPath + "/merge_requests", token);
        return new io.releasehub.application.gitoperation.GitMergeWorkflow(client, MERGE_READINESS_MAX_ATTEMPTS, () -> {
            try { Thread.sleep(MERGE_READINESS_POLL_MILLIS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
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
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.POST, new HttpEntity<>(body, headers(token)),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            confirmedRefSha(response, tagName, ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 408) throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            return false;
        } catch (RestClientException e) {
            throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
        }
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        return mergeWorkflow(repoCloneUrl, token).probe(sourceBranch, targetBranch);
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason) {
        String archivedBranchName = "archive/" + reason + "/" + branchName.replace("/", "-");
        if (!createBranch(repoCloneUrl, token, archivedBranchName, branchName)) {
            log.warn("Archive creation rejected; retaining source branch");
            return false;
        }
        return deleteBranch(repoCloneUrl, token, branchName);
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
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                Object id = responseBody.get("id");
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
            String branchPrefix = prefix == null ? "" : prefix;
            String endpoint = branchPrefix.isBlank()
                    ? String.format("%s/api/v4/projects/%s/repository/branches?per_page=100",
                    repoRef.baseUrl, repoRef.encodedPath)
                    : String.format("%s/api/v4/projects/%s/repository/branches?search=%s&per_page=100",
                    repoRef.baseUrl, repoRef.encodedPath, urlEncode(branchPrefix));
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri(endpoint), HttpMethod.GET, new HttpEntity<>(headers(token)),
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
            return BranchStatus.present(confirmedRefSha(response, branchName, ErrorCode.GIT_IDENTITY_UNAVAILABLE));
        } catch (HttpClientErrorException.NotFound e) {
            return BranchStatus.missing();
        } catch (RestClientException e) {
            throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
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

    private record RepoRef(String baseUrl, String encodedPath) {
    }

}
