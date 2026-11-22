package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitOperationContext;
import io.releasehub.application.gitoperation.GitMergeRequestPort.SourceRevision;
import io.releasehub.application.gitoperation.GitWriteResult;

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

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MERGE_READINESS_MAX_ATTEMPTS = 80;
    private static final long MERGE_READINESS_POLL_MILLIS = 250L;

    private RestTemplate restTemplate;
    private final GitRemoteIdentityResolver identities;

    public GitLabGitBranchAdapter(RestTemplateBuilder builder, GitRemoteIdentityResolver identities) {
        this.restTemplate = GitHttpClients.create(builder);
        this.identities = identities;
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
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.POST, entity, JsonNode.class);
            if (createdBranchSha(response.getStatusCode().value(), response.getBody(), branchName) == null) {
                throw BusinessException.of(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN);
            }
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
        return mergeWorkflow(repoCloneUrl, token, null).merge(sourceBranch, targetBranch, commitMessage);
    }

    @Override
    public GitWriteResult<MrEvidence> ensureMergeRequest(String repoCloneUrl, String token, String sourceBranch,
                                                        String targetBranch, String message) {
        var prepared = GitOperationContext.unsentOperation();
        SourceRevision original = null;
        if (prepared != null) {
            JsonNode target;
            try {
                target = JSON.readTree(prepared.targetJson());
            } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
                throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
            }
            var request = GitLabMergeRequestAdapter.recordedCreateMrRequest(target, prepared.id());
            if (request == null || !"CREATE_MR".equals(prepared.action()) || !"GITLAB".equals(prepared.provider())
                    || !GitRemoteIdentityResolver.validProjectId(prepared.projectId())
                    || !prepared.projectId().equals(GitOperationContext.expectedProjectId())
                    || !java.util.Objects.equals(sourceBranch, request.path("source_branch").textValue())
                    || !java.util.Objects.equals(targetBranch, request.path("target_branch").textValue())) {
                throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
            }
            original = new SourceRevision(
                    prepared.projectId(), target.path("sha").textValue());
        }
        return mergeWorkflow(repoCloneUrl, token, original == null ? null : original.projectId())
                .ensureMergeRequest(sourceBranch, targetBranch, message, original);
    }

    @Override
    public MergeResult tryMergeRequest(String repoCloneUrl, String token, MrEvidence checkpoint, String message) {
        if (!io.releasehub.application.gitoperation.GitMergeWorkflow.isValidCheckpoint(checkpoint)) {
            return MergeResult.unknown("MR_CHECKPOINT_INVALID").withEvidence(checkpoint);
        }
        return mergeWorkflow(repoCloneUrl, token, checkpoint.projectId()).tryMergeRequest(checkpoint, message);
    }

    /** The complete CREATE_BRANCH body; ordering is stable and the frozen ref retains its original bytes. */
    public static com.fasterxml.jackson.databind.node.ObjectNode canonicalBranchRequest(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2
                || !body.path("branch").isTextual() || body.path("branch").textValue().isBlank()
                || !body.path("ref").isTextual() || !validSha(body.path("ref").textValue())) return null;
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("branch", body.path("branch").textValue()).put("ref", body.path("ref").textValue());
    }

    /** CREATE_BRANCH has no query parameters; normal dispatch and recovery fingerprint the same compact body. */
    public static String branchRequestHash(String remoteKey, JsonNode body) {
        var canonical = canonicalBranchRequest(body);
        if (canonical == null || remoteKey == null || remoteKey.isBlank()) return null;
        return GitRemoteIdentityResolver.hash("POST|" + remoteKey + "|/repository/branches||"
                + GitRemoteIdentityResolver.hash(canonical.toString().getBytes(StandardCharsets.UTF_8)) + "|");
    }

    /** Strict persisted CREATE_BRANCH target; plan correspondence remains the task journal's responsibility. */
    public static com.fasterxml.jackson.databind.node.ObjectNode recordedBranchRequest(JsonNode target) {
        if (target == null || !target.isObject() || target.size() != 5
                || !target.path("method").isTextual() || !"POST".equals(target.path("method").textValue())
                || !target.path("suffix").isTextual()
                || !"/repository/branches".equals(target.path("suffix").textValue())
                || !target.path("sourceRef").isTextual() || target.path("sourceRef").textValue().isBlank()) return null;
        var body = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        body.set("branch", target.path("branch"));
        body.set("ref", target.path("ref"));
        return canonicalBranchRequest(body);
    }

    /** Canonical creation proof shared by the client, HTTP journal and database projection. */
    public static String createdBranchSha(int status, JsonNode body, String expectedBranch) {
        if (status != 201 || body == null || !body.isObject() || expectedBranch == null || expectedBranch.isBlank()
                || !body.path("name").isTextual() || !expectedBranch.equals(body.path("name").textValue())
                || !body.path("commit").isObject() || !body.path("commit").path("id").isTextual()) {
            return null;
        }
        String sha = body.path("commit").path("id").textValue();
        return validSha(sha) ? sha : null;
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

    private io.releasehub.application.gitoperation.GitMergeWorkflow mergeWorkflow(String cloneUrl, String token, String expectedProjectId) {
        var identity = resolveIdentity(cloneUrl, token, "/merge_requests", expectedProjectId);
        var prepared = GitOperationContext.unsentOperation();
        if (prepared != null && "CREATE_MR".equals(prepared.action())
                && !java.util.Objects.equals(prepared.remoteKey(), identity.remoteKey())) {
            throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
        }
        var client = new GitLabMergeRequestAdapter(restTemplate, identity, token);
        return new io.releasehub.application.gitoperation.GitMergeWorkflow(client, MERGE_READINESS_MAX_ATTEMPTS, () -> {
            try { Thread.sleep(MERGE_READINESS_POLL_MILLIS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }

    @Override
    public GitReadResult<TagEvidence> readTag(String repoCloneUrl, String token, String expectedProjectId, String tagName) {
        requireProjectRef(expectedProjectId, tagName);
        RepoRef repoRef = parseRepoRef(repoCloneUrl);
        String endpoint = repoRef.baseUrl + "/api/v4/projects/" + repoRef.encodedPath
                + "/repository/tags/" + urlEncode(tagName);
        GitRemoteIdentityResolver.Identity identity;
        try {
            identity = identities.resolve(uri(endpoint), expectedHeaders(token, expectedProjectId, null));
        } catch (BusinessException e) {
            return GitReadResult.unavailable();
        }
        // A namespace lookup failure is not a missing tag. Read the confirmed numeric project only.
        try {
            var response = restTemplate.exchange(uri(identity.baseUrl() + "/api/v4/projects/" + identity.projectId()
                    + "/repository/tags/" + urlEncode(tagName)), HttpMethod.GET,
                    new HttpEntity<>(headers(token)), JsonNode.class);
            JsonNode body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null
                    || !body.path("name").isTextual() || body.path("name").asText().isBlank()
                    || !validSha(body.path("commit").path("id").asText())) {
                return new GitReadResult<>(GitReadResult.Status.INCOMPLETE, null);
            }
            if (!tagName.equals(body.path("name").asText())) {
                return new GitReadResult<>(GitReadResult.Status.MISMATCH, null);
            }
            return GitReadResult.found(new TagEvidence(identity.projectId(), tagName, body.path("commit").path("id").asText()));
        } catch (HttpClientErrorException.NotFound e) {
            return new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null);
        } catch (HttpClientErrorException e) {
            return new GitReadResult<>(e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
                    ? GitReadResult.Status.FORBIDDEN : GitReadResult.Status.UNAVAILABLE, null);
        } catch (RestClientException e) {
            return GitReadResult.unavailable();
        }
    }

    @Override
    public GitWriteResult<TagEvidence> createTag(String repoCloneUrl, String token, String expectedProjectId,
                                                String tagName, String targetSha, String message) {
        requireProjectRef(expectedProjectId, tagName);
        if (!validSha(targetSha)) throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = repoRef.baseUrl + "/api/v4/projects/" + repoRef.encodedPath + "/repository/tags";
            Map<String, String> body = new HashMap<>();
            body.put("tag_name", tagName);
            body.put("ref", targetSha);
            body.put("message", message == null ? "" : message);
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.POST,
                    new HttpEntity<>(body, expectedHeaders(token, expectedProjectId, null)), JsonNode.class);
            JsonNode node = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || node == null
                    || !tagName.equals(node.path("name").asText())
                    || !targetSha.equals(node.path("commit").path("id").asText())) {
                return GitWriteResult.unknown("TAG_RESPONSE_UNCONFIRMED");
            }
            return GitWriteResult.applied(new TagEvidence(expectedProjectId, tagName, node.path("commit").path("id").asText()));
        } catch (HttpClientErrorException e) {
            return writeRejection(e);
        } catch (RestClientException e) {
            return GitWriteResult.unknown("RESPONSE_UNCONFIRMED");
        }
    }

    @Override
    public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
        return mergeWorkflow(repoCloneUrl, token, null).probe(sourceBranch, targetBranch);
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
    public GitWriteResult<PipelineEvidence> triggerPipeline(String repoCloneUrl, String token, String expectedProjectId,
                                                            String ref, String expectedSha) {
        requireProjectRef(expectedProjectId, ref);
        if (!validSha(expectedSha)) throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        try {
            RepoRef repoRef = parseRepoRef(repoCloneUrl);
            String endpoint = repoRef.baseUrl + "/api/v4/projects/" + repoRef.encodedPath + "/pipeline?ref=" + urlEncode(ref);
            var response = restTemplate.exchange(uri(endpoint), HttpMethod.POST,
                    new HttpEntity<>(expectedHeaders(token, expectedProjectId, expectedSha)), JsonNode.class);
            PipelineEvidence evidence = pipelineEvidence(response.getBody(), expectedProjectId, ref, expectedSha);
            return response.getStatusCode().is2xxSuccessful() && evidence != null
                    ? GitWriteResult.applied(evidence) : GitWriteResult.unknown("PIPELINE_RESPONSE_UNCONFIRMED");
        } catch (HttpClientErrorException e) {
            return writeRejection(e);
        } catch (RestClientException e) {
            return GitWriteResult.unknown("RESPONSE_UNCONFIRMED");
        }
    }

    /** Same allowlisted evidence check is used by the HTTP journal and the application-facing adapter. */
    static PipelineEvidence pipelineEvidence(JsonNode body, String expectedProjectId, String ref, String expectedSha) {
        if (!GitRemoteIdentityResolver.validProjectId(expectedProjectId) || ref == null || ref.isBlank() || !validSha(expectedSha)
                || body == null || !body.path("project_id").isIntegralNumber() || !body.path("id").isIntegralNumber()
                || !GitRemoteIdentityResolver.validProjectId(body.path("id").asText())
                || !expectedProjectId.equals(body.path("project_id").asText())
                || !body.path("ref").isTextual() || !ref.equals(body.path("ref").asText())
                || !validSha(body.path("sha").asText()) || !expectedSha.equals(body.path("sha").asText())
                || !body.path("web_url").isTextual() || !validEvidenceUrl(body.path("web_url").asText())) return null;
        return new PipelineEvidence(expectedProjectId, body.path("id").asText(), ref,
                body.path("sha").asText(), body.path("web_url").asText());
    }

    static boolean validSha(String sha) {
        return sha != null && sha.matches("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})");
    }

    private static boolean validEvidenceUrl(String url) {
        try {
            URI parsed = URI.create(url);
            return ("https".equals(parsed.getScheme()) || "http".equals(parsed.getScheme())) && parsed.getHost() != null
                    && parsed.getUserInfo() == null && parsed.getQuery() == null && parsed.getFragment() == null
                    && !parsed.getPath().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void requireProjectRef(String expectedProjectId, String ref) {
        if (!GitRemoteIdentityResolver.validProjectId(expectedProjectId) || ref == null || ref.isBlank())
            throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
    }

    private HttpHeaders expectedHeaders(String token, String expectedProjectId, String expectedSha) {
        HttpHeaders headers = headers(token);
        headers.set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID, expectedProjectId);
        if (expectedSha != null) headers.set(GitRemoteIdentityResolver.EXPECTED_SHA, expectedSha);
        return headers;
    }

    private static <T> GitWriteResult<T> writeRejection(HttpClientErrorException error) {
        return error.getStatusCode().value() == 408 ? GitWriteResult.unknown("HTTP_408")
                : GitWriteResult.rejected("HTTP_" + error.getStatusCode().value());
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
        String suffix="/repository/branches/"+urlEncode(branchName);
        var identity=resolveIdentity(repoCloneUrl,token,suffix,null);
        try {
            String endpoint=identity.baseUrl()+"/api/v4/projects/"+identity.projectId()+suffix;
            ResponseEntity<Map<String,Object>> response=restTemplate.exchange(uri(endpoint),HttpMethod.GET,
                    new HttpEntity<>(headers(token)),new ParameterizedTypeReference<>() {});
            return BranchStatus.present(identity.projectId(),confirmedRefSha(response,branchName,ErrorCode.GIT_IDENTITY_UNAVAILABLE));
        } catch (HttpClientErrorException.NotFound e) {
            return BranchStatus.missing(identity.projectId());
        } catch (RestClientException e) {
            throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
        }
    }

    private GitRemoteIdentityResolver.Identity resolveIdentity(String cloneUrl,String token,String suffix,String expectedProjectId) {
        RepoRef ref=parseRepoRef(cloneUrl);
        HttpHeaders headers=headers(token);
        String expected=expectedProjectId==null?GitOperationContext.expectedProjectId():expectedProjectId;
        if(expected!=null)headers.set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID,expected);
        return GitRemoteIdentityResolver.confirm(identities.resolve(
                uri(ref.baseUrl+"/api/v4/projects/"+ref.encodedPath+suffix),headers),expected);
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
