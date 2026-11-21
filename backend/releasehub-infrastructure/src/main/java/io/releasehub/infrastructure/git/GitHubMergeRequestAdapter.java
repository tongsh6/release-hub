package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitMergeRequestPort;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GitHub 可合并性仅查询已有 PR，不创建或清理临时资源。 */
final class GitHubMergeRequestAdapter implements GitMergeRequestPort {
    private final RestTemplate http;
    private final String endpoint;
    private final String projectEndpoint;
    private final String owner;
    private final String token;
    private String projectId;

    GitHubMergeRequestAdapter(RestTemplate http, String endpoint, String token) {
        this.http = http;
        this.endpoint = endpoint;
        var parsed = GitRemoteIdentityResolver.parse(URI.create(endpoint));
        this.projectEndpoint = parsed.projectEndpoint();
        this.owner = parsed.locator().split("/")[0];
        this.token = token;
    }

    @Override public GitReadResult<SourceRevision> readSource(String source) {
        var project = getMap(projectEndpoint);
        if (project.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(project.status(), null);
        if (!(project.evidence().get("id") instanceof Number)) return incomplete();
        projectId = string(project.evidence().get("id"));
        var branch = getMap(projectEndpoint + "/branches/" + encode(source));
        if (branch.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(branch.status(), null);
        String sha = string(map(branch.evidence().get("commit")).get("sha"));
        if (!source.equals(branch.evidence().get("name")) || sha == null || !sha.matches("[0-9a-fA-F]{40,64}")) return incomplete();
        return GitReadResult.found(new SourceRevision(projectId, sha));
    }

    @Override public GitReadResult<List<MergeRequest>> find(String source, String target, State state) {
        if (projectId == null) return incomplete();
        var result = new ArrayList<MergeRequest>();
        for (int page = 1; page <= 20; page++) {
            try {
                var response = http.exchange(URI.create(endpoint + "?state=" + (state == State.OPENED ? "open" : "closed")
                                + "&head=" + encode(owner + ":" + source) + "&base=" + encode(target) + "&per_page=100&page=" + page),
                        HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                var body = response.getBody();
                if (body == null) return incomplete();
                for (var row : body) {
                    var head = map(row.get("head"));
                    if (!projectId.equals(string(map(head.get("repo")).get("id")))) continue;
                    var mr = evidence(row);
                    if (!complete(mr)) return incomplete();
                    if (!source.equals(mr.sourceBranch()) || !target.equals(mr.targetBranch()) || !projectId.equals(mr.projectId())) {
                        return new GitReadResult<>(GitReadResult.Status.MISMATCH, null);
                    }
                    if (mr.state() == state) result.add(mr);
                }
                String link = response.getHeaders().getFirst("Link");
                if (link == null ? body.size() < 100 : !link.contains("rel=\"next\"")) {
                    return GitReadResult.found(List.copyOf(result));
                }
            } catch (HttpClientErrorException e) { return readError(e); }
            catch (RestClientException e) { return GitReadResult.unavailable(); }
        }
        return incomplete();
    }

    @Override public GitReadResult<Boolean> contains(String target, String sourceSha) {
        var comparison = getMap(projectEndpoint + "/compare/" + encode(sourceSha) + "..." + encode(target));
        if (comparison.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(comparison.status(), null);
        String status = string(comparison.evidence().get("status"));
        if ("ahead".equals(status) || "identical".equals(status)) return GitReadResult.found(true);
        if ("behind".equals(status) || "diverged".equals(status)) return GitReadResult.found(false);
        return incomplete();
    }

    @Override public GitReadResult<MergeRequest> read(String id) {
        var response = getMap(endpoint + "/" + encode(id));
        if (response.status() != GitReadResult.Status.FOUND) return new GitReadResult<>(response.status(), null);
        var mr = evidence(response.evidence());
        return complete(mr) && id.equals(mr.id()) ? GitReadResult.found(mr) : incomplete();
    }

    @Override public GitWriteResult<MergeRequest> create(String source, String target, String expectedSourceSha, String title) {
        return GitWriteResult.rejected("UNSUPPORTED");
    }

    @Override public GitWriteResult<MergeRequest> merge(String id, String sourceSha, String message) {
        return GitWriteResult.rejected("UNSUPPORTED");
    }

    private GitReadResult<Map<String, Object>> getMap(String uri) {
        try {
            var body = http.exchange(URI.create(uri), HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return body == null ? incomplete() : GitReadResult.found(body);
        } catch (HttpClientErrorException e) { return readError(e); }
        catch (RestClientException e) { return GitReadResult.unavailable(); }
    }

    private MergeRequest evidence(Map<String, Object> body) {
        if (body == null) return null;
        var head = map(body.get("head"));
        var base = map(body.get("base"));
        State state = Boolean.TRUE.equals(body.get("merged")) || body.get("merged_at") != null ? State.MERGED
                : "open".equals(body.get("state")) ? State.OPENED : "closed".equals(body.get("state")) ? State.CLOSED : State.UNKNOWN;
        Readiness readiness = Boolean.TRUE.equals(body.get("mergeable")) ? Readiness.MERGEABLE
                : Boolean.FALSE.equals(body.get("mergeable")) ? Readiness.BLOCKED : Readiness.PENDING;
        MergeBlockReason reason = readiness == Readiness.BLOCKED ? MergeBlockReason.CONFLICT : null;
        if ("blocked".equals(body.get("mergeable_state")) || Boolean.TRUE.equals(body.get("draft"))) {
            readiness = Readiness.UNKNOWN;
            reason = MergeBlockReason.UNKNOWN;
        }
        return new MergeRequest(string(body.get("number")), string(body.get("html_url")), string(map(base.get("repo")).get("id")),
                string(head.get("ref")), string(base.get("ref")), string(head.get("sha")), string(body.get("merge_commit_sha")),
                state, readiness, reason, string(body.get("mergeable_state")));
    }

    private static boolean complete(MergeRequest mr) {
        return mr != null && mr.id() != null && mr.url() != null && mr.projectId() != null
                && mr.sourceBranch() != null && mr.targetBranch() != null && mr.state() != State.UNKNOWN;
    }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> m ? m : Map.of(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static <T> GitReadResult<T> incomplete() { return new GitReadResult<>(GitReadResult.Status.INCOMPLETE, null); }
    private static <T> GitReadResult<T> readError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        return new GitReadResult<>(status == 401 || status == 403 ? GitReadResult.Status.FORBIDDEN
                : status == 404 ? GitReadResult.Status.NOT_FOUND : GitReadResult.Status.UNAVAILABLE, null);
    }
    private HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }
}
