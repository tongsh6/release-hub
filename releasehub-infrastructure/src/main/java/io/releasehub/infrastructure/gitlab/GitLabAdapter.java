package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.settings.SettingsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabAdapter implements GitLabPort {

    private final SettingsPort settingsPort;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public long resolveProjectId(String repoCloneUrl) {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            throw new IllegalStateException("GitLab settings not configured");
        }
        String baseUrl = normalizeBaseUrl(settings.get().baseUrl());
        String token = settings.get().tokenMasked();

        String projectPath = extractProjectPath(repoCloneUrl);
        String encoded = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);

        String url = String.format("%s/api/v4/projects/%s", baseUrl, encoded);
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {
                }
        );
        Map<String, Object> body = response.getBody();
        if (body == null || body.get("id") == null) {
            throw new IllegalStateException("GitLab project not found by cloneUrl");
        }
        Object id = body.get("id");
        if (id instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(id));
    }

    @Override
    public boolean branchExists(long projectId, String ref) {
        // Implementation omitted for brevity in this step, but should use real API
        return true;
    }

    @Override
    public Optional<MrInfo> ensureMrInfo(long projectId, String source, String target) {
        // Implementation omitted for brevity
        return Optional.of(new MrInfo(false, false, null, null));
    }

    @Override
    public GateSummary fetchGateSummary(long projectId) {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            return new GateSummary(false, false, false, false);
        }
        String baseUrl = normalizeBaseUrl(settings.get().baseUrl());
        String token = settings.get().tokenMasked();

        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        boolean protectedBranch = false;
        boolean approvalRequired = false;
        boolean pipelineGate = false;

        try {
            String projectUrl = String.format("%s/api/v4/projects/%d", baseUrl, projectId);
            ResponseEntity<Map<String, Object>> projectResp = restTemplate.exchange(
                    projectUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> project = projectResp.getBody();
            if (project != null) {
                pipelineGate = asBoolean(project.get("only_allow_merge_if_pipeline_succeeds"));
                Object approvals = project.get("approvals_before_merge");
                if (approvals instanceof Number n) {
                    approvalRequired = n.intValue() > 0;
                } else if (approvals != null) {
                    try {
                        approvalRequired = Integer.parseInt(String.valueOf(approvals)) > 0;
                    } catch (NumberFormatException ignored) {
                    }
                }

                String defaultBranch = project.get("default_branch") != null ? String.valueOf(project.get("default_branch")) : null;
                if (defaultBranch != null && !defaultBranch.isBlank()) {
                    String protectedUrl = String.format("%s/api/v4/projects/%d/protected_branches?per_page=100", baseUrl, projectId);
                    ResponseEntity<List<Map<String, Object>>> pbResp = restTemplate.exchange(
                            protectedUrl,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<>() {
                            }
                    );
                    List<Map<String, Object>> branches = pbResp.getBody();
                    if (branches != null) {
                        protectedBranch = branches.stream()
                                                  .map(b -> b.get("name"))
                                                  .filter(v -> v != null)
                                                  .map(String::valueOf)
                                                  .anyMatch(defaultBranch::equals);
                    }
                }
            }
            return new GateSummary(protectedBranch, approvalRequired, pipelineGate, false);
        } catch (Exception e) {
            log.warn("Failed to fetch gate summary for project {}: {}", projectId, e.getMessage());
            return new GateSummary(false, false, false, true);
        }
    }

    @Override
    public BranchStatistics fetchBranchStatistics(long projectId) {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            return new BranchStatistics(0, 0, 0);
        }
        String baseUrl = normalizeBaseUrl(settings.get().baseUrl());
        String token = settings.get().tokenMasked();

        String url = String.format("%s/api/v4/projects/%d/repository/branches?per_page=100", baseUrl, projectId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );

            List<Map<String, Object>> branches = response.getBody();
            if (branches == null) {
                return new BranchStatistics(0, 0, 0);
            }

            int total = branches.size();
            // Assuming all returned are active for now, or check 'commit' date if needed
            int active = total;

            // Check compliance
            Pattern compliantPattern = Pattern.compile("^(main|master|develop|feature/.*|fix/.*|release/.*|hotfix/.*)$");
            int nonCompliant = (int) branches.stream()
                                             .map(b -> (String) b.get("name"))
                                             .filter(name -> !compliantPattern.matcher(name).matches())
                                             .count();

            return new BranchStatistics(total, active, nonCompliant);

        } catch (Exception e) {
            log.error("Failed to fetch branch statistics for project {}", projectId, e);
            return new BranchStatistics(0, 0, 0);
        }
    }

    @Override
    public MrStatistics fetchMrStatistics(long projectId) {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            return new MrStatistics(0, 0, 0, 0);
        }
        String baseUrl = normalizeBaseUrl(settings.get().baseUrl());
        String token = settings.get().tokenMasked();

        int open = getCount(baseUrl, projectId, token, "merge_requests", "opened");
        int merged = getCount(baseUrl, projectId, token, "merge_requests", "merged");
        int closed = getCount(baseUrl, projectId, token, "merge_requests", "closed");
        int total = open + merged + closed;

        return new MrStatistics(total, open, merged, closed);
    }

    private int getCount(String baseUrl, long projectId, String token, String resource, String state) {
        String url = String.format("%s/api/v4/projects/%d/%s?per_page=1", baseUrl, projectId, resource);
        if (state != null) {
            url += "&state=" + state;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            String total = response.getHeaders().getFirst("X-Total");
            if (total != null) {
                return Integer.parseInt(total);
            }
            // Fallback if X-Total not present (e.g. small result set or specific gitlab versions)
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to get count for {} {}: {}", resource, state, e.getMessage());
            return 0;
        }
    }

    private boolean asBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String extractProjectPath(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new IllegalArgumentException("cloneUrl is blank");
        }

        Matcher sshMatch = Pattern.compile("^git@([^:]+):(.+?)(\\.git)?$").matcher(cloneUrl.trim());
        if (sshMatch.find()) {
            return sshMatch.group(2);
        }
        Matcher httpsMatch = Pattern.compile("^https?://([^/]+)/(.+?)(\\.git)?$").matcher(cloneUrl.trim());
        if (httpsMatch.find()) {
            return httpsMatch.group(2);
        }
        throw new IllegalArgumentException("Unsupported cloneUrl format");
    }
}
