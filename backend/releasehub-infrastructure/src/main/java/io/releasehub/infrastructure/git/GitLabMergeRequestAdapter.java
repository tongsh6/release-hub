package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
final class GitLabMergeRequestAdapter implements GitMergeRequestPort {
    private static final int MAX_PAGES = 20;
    private static final ObjectMapper ERROR_JSON = new ObjectMapper();
    private final RestTemplate http;
    private final String endpoint;
    private final String projectEndpoint;
    private final String token;
    private String projectId;

    GitLabMergeRequestAdapter(RestTemplate http, String endpoint, String token) {
        this.http = http;
        this.endpoint = endpoint;
        this.projectEndpoint = GitRemoteIdentityResolver.parse(URI.create(endpoint)).projectEndpoint();
        this.token = token;
    }

    @Override public GitReadResult<SourceRevision> readSource(String source) {
        var project = resolveProject();
        if (project.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(project.status(), null);
        var branch = getMap(projectEndpoint + "/repository/branches/" + encode(source));
        if (branch.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(branch.status(), null);
        var body = branch.evidence();
        String sha = string(map(body.get("commit")).get("id"));
        if (!source.equals(body.get("name")) || !validSha(sha)) return incomplete();
        return GitReadResult.found(new SourceRevision(projectId, sha));
    }

    @Override public GitReadResult<List<MergeRequest>> find(String source, String target, State state) {
        var project = resolveProject();
        if (project.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(project.status(), null);
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
        var mr = evidence(result.evidence());
        if (!complete(mr) || !id.equals(mr.id())) return incomplete();
        return GitReadResult.found(mr);
    }

    @Override public GitWriteResult<MergeRequest> create(String source, String target, String title) {
        return write(endpoint, HttpMethod.POST, Map.of("source_branch", source, "target_branch", target,
                "title", title, "remove_source_branch", false), false);
    }

    @Override public GitWriteResult<MergeRequest> merge(String id, String sourceSha, String message) {
        if (!validSha(sourceSha)) return GitWriteResult.rejected("SOURCE_REVISION_REQUIRED");
        return write(endpoint + "/" + encode(id) + "/merge", HttpMethod.PUT,
                Map.of("merge_commit_message", message == null ? "" : message, "sha", sourceSha,
                        "should_remove_source_branch", false), true);
    }

    private GitWriteResult<MergeRequest> write(String uri, HttpMethod method, Map<String, ?> body, boolean merging) {
        try {
            var response = http.exchange(URI.create(uri), method, new HttpEntity<>(body, headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            var mr = evidence(response.getBody());
            if (!response.getStatusCode().is2xxSuccessful() || !complete(mr)
                    || merging && (mr.state() != State.MERGED || !validSha(mr.sourceSha()))) {
                return new GitWriteResult<>(GitWriteResult.Outcome.UNKNOWN, mr, "INCOMPLETE_RESPONSE");
            }
            return GitWriteResult.applied(mr);
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 408) return GitWriteResult.unknown("HTTP_408");
            return GitWriteResult.rejected(rejectionReason(code, rejectedBody(e.getResponseBodyAsByteArray())));
        } catch (RestClientException e) {
            return GitWriteResult.unknown("RESPONSE_UNCONFIRMED");
        }
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

    private GitReadResult<String> resolveProject() {
        if (projectId != null) return GitReadResult.found(projectId);
        var read = getMap(projectEndpoint);
        if (read.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(read.status(), null);
        Object id = read.evidence().get("id");
        if (!(id instanceof Number) || ((Number) id).longValue() <= 0) return incomplete();
        projectId = string(id);
        return GitReadResult.found(projectId);
    }

    private GitReadResult<Map<String, Object>> getMap(String uri) {
        try {
            var response = http.exchange(URI.create(uri), HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
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

    private MergeRequest evidence(Map<String, Object> body) {
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
        return mr != null && mr.id() != null && mr.projectId() != null && mr.sourceBranch() != null
                && mr.targetBranch() != null && mr.url() != null && mr.state() != State.UNKNOWN;
    }
    private static boolean validSha(String sha) { return sha != null && sha.matches("[0-9a-fA-F]{40,64}"); }
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
