package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.releasehub.application.gitoperation.GitMergeRequestPort;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** GitLab REST 原子请求适配器。列表必须读完分页，写入最多一次且不自动关闭 MR。 */
public final class GitLabMergeRequestAdapter implements GitMergeRequestPort {
    private static final int MAX_PAGES = 20;
    private static final ObjectMapper ERROR_JSON = new ObjectMapper();
    private final RestTemplate http;
    private final String endpoint;
    private final String projectEndpoint;
    private final String writeEndpoint;
    private final String token;
    private final String projectId;

    GitLabMergeRequestAdapter(RestTemplate http, GitRemoteIdentityResolver.Identity identity, String token) {
        this.http = http;
        this.projectId = identity.projectId();
        this.projectEndpoint = identity.baseUrl() + "/api/v4/projects/" + projectId;
        this.endpoint = projectEndpoint + "/merge_requests";
        // The interceptor resolves the registered namespace again, checks expectedProjectId, then dispatches numerically.
        this.writeEndpoint = identity.endpoint().projectEndpoint() + "/merge_requests";
        this.token = token;
    }

    @Override public GitReadResult<SourceRevision> readSource(String source) {
        var branch = getMap(projectEndpoint + "/repository/branches/" + encode(source));
        if (branch.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(branch.status(), null);
        var body = branch.evidence();
        String sha = string(map(body.get("commit")).get("id"));
        if (!source.equals(body.get("name")) || !validSha(sha)) return incomplete();
        return GitReadResult.found(new SourceRevision(projectId, sha));
    }

    @Override public GitReadResult<List<MergeRequest>> find(String source, String target, State state) {
        if (state != State.OPENED && state != State.MERGED) return new GitReadResult<>(GitReadResult.Status.UNSUPPORTED, null);
        var pages = readPages(endpoint + "?scope=all&state=" + state.name().toLowerCase(Locale.ROOT)
                + "&source_branch=" + encode(source) + "&target_branch=" + encode(target));
        if (pages.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(pages.status(), null);
        var result = new ArrayList<MergeRequest>();
        for (var body : pages.evidence()) {
            if (body.get("source_project_id") == null || body.get("target_project_id") == null) return incomplete();
            // 同名的 fork 分支不是本仓库的 source，不能复用。
            if (!projectId.equals(string(body.get("source_project_id")))) continue;
            if (!projectId.equals(string(body.get("target_project_id")))) return mismatch();
            var mr = evidence(body);
            if (!complete(mr) || !projectId.equals(mr.projectId())) return incomplete();
            if (!source.equals(mr.sourceBranch()) || !target.equals(mr.targetBranch()) || mr.state() != state) return mismatch();
            result.add(mr);
        }
        return GitReadResult.found(List.copyOf(result));
    }

    @Override public GitReadResult<Boolean> contains(String target, String sourceSha) {
        if (!validSha(sourceSha)) return incomplete();
        var refs = readPages(projectEndpoint + "/repository/commits/" + sourceSha + "/refs?type=branch");
        if (refs.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(refs.status(), null);
        for (var ref : refs.evidence()) {
            if (!(ref.get("name") instanceof String) || !"branch".equals(ref.get("type"))) return incomplete();
        }
        return GitReadResult.found(refs.evidence().stream().anyMatch(ref -> target.equals(ref.get("name"))));
    }

    @Override public GitReadResult<MergeRequest> read(String id) {
        var result = getMap(endpoint + "/" + encode(id));
        if (result.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(result.status(), null);
        JsonNode body = ERROR_JSON.valueToTree(result.evidence());
        for (String field : List.of("project_id", "source_project_id", "target_project_id")) {
            JsonNode project = body.path(field);
            if (!positiveInteger(project)) return incomplete();
            if (!projectId.equals(project.asText())) return mismatch();
        }
        var mr = evidence(result.evidence());
        if (!complete(mr) || !id.equals(mr.id())) return incomplete();
        return GitReadResult.found(mr);
    }

    @Override public GitWriteResult<MergeRequest> create(String source, String target, String expectedSourceSha, String title) {
        if (!validSha(expectedSourceSha)) return GitWriteResult.rejected("SOURCE_REVISION_REQUIRED");
        return write(writeEndpoint, HttpMethod.POST, Map.of("source_branch", source, "target_branch", target,
                "title", title, "description", "", "remove_source_branch", false), false, expectedSourceSha, null);
    }

    @Override public GitWriteResult<MergeRequest> merge(String id, String sourceSha, String message) {
        if (!validSha(sourceSha)) return GitWriteResult.rejected("SOURCE_REVISION_REQUIRED");
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("sha", sourceSha);
        body.put("merge_commit_message", message);
        body.put("should_remove_source_branch", false);
        return write(writeEndpoint + "/" + encode(id) + "/merge", HttpMethod.PUT, body, true, sourceSha, id);
    }

    private GitWriteResult<MergeRequest> write(String uri, HttpMethod method, Map<String, ?> body,
            boolean merging, String sourceSha, String expectedIid) {
        try {
            var writeHeaders = headers();
            writeHeaders.set(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID, projectId);
            writeHeaders.set(GitRemoteIdentityResolver.EXPECTED_SHA, sourceSha);
            Object outbound = body;
            if (merging) {
                outbound = canonicalMergeRequest(ERROR_JSON.valueToTree(body));
                if (outbound == null) return GitWriteResult.rejected("MERGE_REQUEST_INVALID");
            }
            var response = http.exchange(URI.create(uri), method, new HttpEntity<>(outbound, writeHeaders),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!merging) {
                var confirmed = confirmedCreatedMr(response.getStatusCode().value(), ERROR_JSON.valueToTree(response.getBody()),
                        projectId, string(body.get("source_branch")), string(body.get("target_branch")), sourceSha);
                return confirmed.map(GitWriteResult::applied).orElseGet(() ->
                        new GitWriteResult<>(GitWriteResult.Outcome.UNKNOWN, createdEvidence(response.getBody()), "INCOMPLETE_RESPONSE"));
            }
            var confirmed = confirmedMergedMr(response.getStatusCode().value(), ERROR_JSON.valueToTree(response.getBody()),
                    projectId, expectedIid, sourceSha);
            return confirmed.map(GitWriteResult::applied).orElseGet(() ->
                    new GitWriteResult<>(GitWriteResult.Outcome.UNKNOWN, evidence(response.getBody()), "INCOMPLETE_RESPONSE"));
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 408) return GitWriteResult.unknown("HTTP_408");
            return GitWriteResult.rejected(rejectionReason(code, rejectedBody(e.getResponseBodyAsByteArray())));
        } catch (RestClientException e) {
            return GitWriteResult.unknown("RESPONSE_UNCONFIRMED");
        }
    }

    /** CREATE 正文的五个有类型字段；入站 marker 与已记录 marker 在各自边界验证。 */
    public static ObjectNode canonicalCreateMrRequest(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 5
                || !body.path("source_branch").isTextual() || body.path("source_branch").textValue().isBlank()
                || !body.path("target_branch").isTextual() || body.path("target_branch").textValue().isBlank()
                || !body.path("title").isTextual() || body.path("title").textValue().isBlank()
                || !body.path("description").isTextual()
                || !body.path("remove_source_branch").isBoolean()
                || body.path("remove_source_branch").booleanValue()) return null;
        return ERROR_JSON.createObjectNode().put("source_branch", body.path("source_branch").textValue())
                .put("target_branch", body.path("target_branch").textValue()).put("title", body.path("title").textValue())
                .put("description", body.path("description").textValue()).put("remove_source_branch", false);
    }

    /** 只从完整原命令重建 POST 正文；不补字段或替换原 operation marker。 */
    public static ObjectNode recordedCreateMrRequest(JsonNode target, String operationId) {
        if (target == null || !target.isObject() || target.size() != 8
                || !"POST".equals(target.path("method").textValue())
                || !"/merge_requests".equals(target.path("suffix").textValue())
                || !validSha(target.path("sha").textValue())
                || operationId == null || operationId.isBlank()
                || operationId.contains("\r") || operationId.contains("\n")) return null;
        ObjectNode fields = target.deepCopy();
        fields.remove(List.of("method", "suffix", "sha"));
        ObjectNode body = canonicalCreateMrRequest(fields);
        if (body == null) return null;
        String prefix = "ReleaseHub-Operation:";
        String description = body.path("description").textValue();
        String marker = prefix + " " + operationId;
        if (!description.endsWith("\n" + marker)
                || description.indexOf(prefix) != description.lastIndexOf(prefix)) return null;
        return body;
    }

    /** CREATE 无 query；expectedSha 仅用于意图与响应证明，不是 POST 的远端 CAS。 */
    public static String createMrRequestHash(String remoteKey, JsonNode body, String expectedSha) {
        ObjectNode canonical = canonicalCreateMrRequest(body);
        if (canonical == null || remoteKey == null || remoteKey.isBlank() || !validSha(expectedSha)) return null;
        return GitRemoteIdentityResolver.hash("POST|" + remoteKey + "|/merge_requests||"
                + GitRemoteIdentityResolver.hash(canonical.toString().getBytes(StandardCharsets.UTF_8)) + "|" + expectedSha);
    }

    /** 原 MERGE 请求只包含这三个有类型字段；不从缺失值补造发送意图。 */
    public static ObjectNode canonicalMergeRequest(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 3
                || !body.path("sha").isTextual() || !validSha(body.path("sha").textValue())
                || !body.path("merge_commit_message").isTextual()
                || !body.path("should_remove_source_branch").isBoolean()
                || body.path("should_remove_source_branch").booleanValue()) return null;
        return ERROR_JSON.createObjectNode().put("sha", body.path("sha").textValue())
                .put("merge_commit_message", body.path("merge_commit_message").textValue())
                .put("should_remove_source_branch", false);
    }

    /** 固定无 query 的 MERGE 封套；正常发送和原意图恢复使用同一紧凑 UTF-8 正文。 */
    public static String mergeRequestHash(String remoteKey, String suffix, JsonNode body) {
        ObjectNode canonical = canonicalMergeRequest(body);
        if (canonical == null || remoteKey == null || remoteKey.isBlank() || suffix == null || suffix.isBlank()) {
            return null;
        }
        byte[] bytes = canonical.toString().getBytes(StandardCharsets.UTF_8);
        return GitRemoteIdentityResolver.hash("PUT|" + remoteKey + "|" + suffix + "|null|"
                + GitRemoteIdentityResolver.hash(bytes) + "|" + canonical.path("sha").textValue());
    }

    /** CREATE 的成功证明与合并就绪状态分开；三个消费者共用同一原始响应判定。 */
    public static java.util.Optional<MergeRequest> confirmedCreatedMr(int status, JsonNode body, String projectId,
            String sourceBranch, String targetBranch, String expectedSourceSha) {
        if (status != 201 || body == null || !body.isObject() || sourceBranch == null || targetBranch == null
                || !validSha(expectedSourceSha)) return java.util.Optional.empty();
        for (String field : List.of("project_id", "source_project_id", "target_project_id")) {
            JsonNode id = body.path(field);
            if (!positiveInteger(id) || !id.asText().equals(projectId)) return java.util.Optional.empty();
        }
        if (!positiveInteger(body.path("iid")) || !body.path("web_url").isTextual()
                || !body.path("source_branch").isTextual() || !sourceBranch.equals(body.path("source_branch").textValue())
                || !body.path("target_branch").isTextual() || !targetBranch.equals(body.path("target_branch").textValue())
                || !body.path("state").isTextual() || !"opened".equals(body.path("state").textValue())
                || !body.path("sha").isTextual() || !validSha(body.path("sha").textValue())
                || !expectedSourceSha.equalsIgnoreCase(body.path("sha").textValue())) return java.util.Optional.empty();
        var mr = createdEvidence(ERROR_JSON.convertValue(body,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return complete(mr) ? java.util.Optional.of(mr) : java.util.Optional.empty();
    }

    /** MERGE 只证明原 IID/source SHA 的远端成功；分支与业务检查点由现有消费者绑定。 */
    public static java.util.Optional<MergeRequest> confirmedMergedMr(int status, JsonNode body, String projectId,
            String iid, String expectedSourceSha) {
        if (status != 200 || body == null || !body.isObject() || !validSha(expectedSourceSha)) {
            return java.util.Optional.empty();
        }
        for (String field : List.of("project_id", "source_project_id", "target_project_id")) {
            JsonNode id = body.path(field);
            if (!positiveInteger(id) || !id.asText().equals(projectId)) return java.util.Optional.empty();
        }
        if (!positiveInteger(body.path("iid")) || !body.path("iid").asText().equals(iid)
                || !body.path("web_url").isTextual()
                || !body.path("source_branch").isTextual() || !body.path("target_branch").isTextual()
                || !body.path("state").isTextual() || !"merged".equals(body.path("state").textValue())
                || !body.path("sha").isTextual() || !validSha(body.path("sha").textValue())
                || !expectedSourceSha.equalsIgnoreCase(body.path("sha").textValue())) return java.util.Optional.empty();
        JsonNode mergeSha = body.get("merge_commit_sha");
        if (mergeSha == null || (!mergeSha.isNull() && (!mergeSha.isTextual() || !validSha(mergeSha.textValue())))) {
            return java.util.Optional.empty();
        }
        var mr = evidence(ERROR_JSON.convertValue(body,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return complete(mr) ? java.util.Optional.of(mr) : java.util.Optional.empty();
    }

    private static boolean positiveInteger(JsonNode value) {
        return value.isIntegralNumber() && value.bigIntegerValue().signum() > 0;
    }

    /** 创建正文可以提供诊断身份，但尚不能提供本次合并的提交结果。 */
    private static MergeRequest createdEvidence(Map<String, Object> body) {
        var mr = evidence(body);
        return mr == null ? null : new MergeRequest(mr.id(), mr.url(), mr.projectId(), mr.sourceBranch(),
                mr.targetBranch(), mr.sourceSha(), null, mr.state(), mr.readiness(), mr.blockReason(), mr.detail());
    }

    /** Provider 拒绝语义只在此分类；安全拦截器在丢弃原始正文前复用这些规则。 */
    static String classifyRejection(int status, JsonNode body) {
        String message = body == null ? "" : body.path("message").toString().toLowerCase(Locale.ROOT);
        if (message.contains("protected branch")) return MergeBlockReason.PROTECTED_BRANCH.name();
        if (status == 401 || status == 403) return MergeBlockReason.PERMISSION_DENIED.name();
        if (message.contains("no commits between")) return "NO_COMMITS";
        if (message.contains("conflict") && status != 409) return MergeBlockReason.CONFLICT.name();
        if (message.contains("approval")) return MergeBlockReason.APPROVAL_REQUIRED.name();
        if (message.contains("pipeline") && message.contains("fail")) return MergeBlockReason.PIPELINE_FAILED.name();
        if (message.contains("pipeline")) return MergeBlockReason.PIPELINE_PENDING.name();
        return "HTTP_" + status;
    }

    private static String rejectionReason(int status, JsonNode body) {
        if (body != null && "REMOTE_WRITE_REJECTED".equals(body.path("message").asText())) {
            String reason = body.path("reason").asText();
            if ("NO_COMMITS".equals(reason) || ("HTTP_" + status).equals(reason)
                    || java.util.Arrays.stream(MergeBlockReason.values()).anyMatch(value -> value.name().equals(reason))) {
                return reason;
            }
            // 未知值不能从响应正文进入业务结果或审计消息。
            return "HTTP_" + status;
        }
        return classifyRejection(status, body);
    }

    private static JsonNode rejectedBody(byte[] bytes) {
        try { return ERROR_JSON.readTree(bytes); }
        catch (java.io.IOException e) { return null; }
    }

    private GitReadResult<Map<String, Object>> getMap(String uri) {
        try {
            var response = http.exchange(URI.create(uri), HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().value() != 200) return incomplete();
            return response.getBody() == null ? incomplete() : GitReadResult.found(response.getBody());
        } catch (HttpClientErrorException e) { return readError(e); }
        catch (RestClientException e) { return GitReadResult.unavailable(); }
    }

    private GitReadResult<List<Map<String, Object>>> readPages(String uri) {
        var result = new ArrayList<Map<String, Object>>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                var response = http.exchange(URI.create(uri + "&per_page=100&page=" + page), HttpMethod.GET,
                        new HttpEntity<>(headers()), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (response.getStatusCode().value() != 200) return incomplete();
                var body = response.getBody();
                if (body == null) return incomplete();
                result.addAll(body);
                String next = response.getHeaders().getFirst("X-Next-Page");
                if (next != null) {
                    if (next.isBlank()) return GitReadResult.found(List.copyOf(result));
                    if (!Integer.toString(page + 1).equals(next)) return incomplete();
                } else if (body.size() < 100) {
                    return GitReadResult.found(List.copyOf(result));
                }
            } catch (HttpClientErrorException e) { return readError(e); }
            catch (RestClientException e) { return GitReadResult.unavailable(); }
        }
        return incomplete();
    }

    private static MergeRequest evidence(Map<String, Object> body) {
        if (body == null) return null;
        State state = switch (string(body.get("state")) == null ? "" : string(body.get("state"))) {
            case "opened" -> State.OPENED;
            case "merged" -> State.MERGED;
            case "closed" -> State.CLOSED;
            default -> State.UNKNOWN;
        };
        String status = string(body.get("detailed_merge_status"));
        MergeBlockReason reason = null;
        Readiness readiness;
        if (Boolean.TRUE.equals(body.get("has_conflicts")) || "conflict".equals(status)) {
            readiness = Readiness.BLOCKED;
            reason = MergeBlockReason.CONFLICT;
        } else if (Boolean.FALSE.equals(map(body.get("user")).get("can_merge")) && state == State.OPENED) {
            readiness = Readiness.BLOCKED;
            reason = MergeBlockReason.PERMISSION_DENIED;
        } else {
            readiness = switch (status == null ? "" : status) {
                case "mergeable" -> Readiness.MERGEABLE;
                case "checking", "preparing", "unchecked", "approvals_syncing" -> Readiness.PENDING;
                case "ci_still_running", "ci_must_pass", "not_approved", "requested_changes" -> Readiness.BLOCKED;
                default -> Readiness.UNKNOWN;
            };
            reason = switch (status == null ? "" : status) {
                case "ci_still_running" -> MergeBlockReason.PIPELINE_PENDING;
                case "ci_must_pass" -> ("failed".equals(map(body.get("head_pipeline")).get("status")) || "canceled".equals(map(body.get("head_pipeline")).get("status")))
                        ? MergeBlockReason.PIPELINE_FAILED : MergeBlockReason.PIPELINE_PENDING;
                case "not_approved", "requested_changes" -> MergeBlockReason.APPROVAL_REQUIRED;
                default -> readiness == Readiness.UNKNOWN ? MergeBlockReason.UNKNOWN : null;
            };
        }
        return new MergeRequest(string(body.get("iid")), string(body.get("web_url")), string(body.get("project_id")),
                string(body.get("source_branch")), string(body.get("target_branch")), string(body.get("sha")),
                string(body.get("merge_commit_sha")), state, readiness, reason, status);
    }

    private static boolean complete(MergeRequest mr) {
        return mr != null && mr.state() != State.UNKNOWN
                && io.releasehub.application.gitoperation.GitMergeWorkflow.isValidCheckpoint(
                        new io.releasehub.application.port.out.GitBranchPort.MrEvidence(mr.projectId(), mr.sourceBranch(),
                                mr.targetBranch(), mr.id(), mr.url(), mr.sourceSha(), mr.mergeSha(),
                                io.releasehub.application.port.out.GitBranchPort.MrDisposition.MR_REUSED, null));
    }
    private static boolean validSha(String sha) { return GitLabGitBranchAdapter.validSha(sha); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> m ? m : Map.of(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static <T> GitReadResult<T> incomplete() { return new GitReadResult<>(GitReadResult.Status.INCOMPLETE, null); }
    private static <T> GitReadResult<T> mismatch() { return new GitReadResult<>(GitReadResult.Status.MISMATCH, null); }
    private static <T> GitReadResult<T> readError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        return new GitReadResult<>(status == 401 || status == 403 ? GitReadResult.Status.FORBIDDEN
                : status == 404 ? GitReadResult.Status.NOT_FOUND : GitReadResult.Status.UNAVAILABLE, null);
    }
    private HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }
}
