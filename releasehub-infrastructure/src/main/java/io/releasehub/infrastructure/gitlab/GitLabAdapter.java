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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabAdapter implements GitLabPort {

    private final SettingsPort settingsPort;
    private final RestTemplate restTemplate = new RestTemplate();

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
        // Implementation omitted for brevity
        return new GateSummary(false, false, false, false);
    }

    @Override
    public BranchStatistics fetchBranchStatistics(long projectId) {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            return new BranchStatistics(0, 0, 0);
        }
        String baseUrl = settings.get().baseUrl();
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
                    new ParameterizedTypeReference<>() {}
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
        String baseUrl = settings.get().baseUrl();
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
                    new ParameterizedTypeReference<>() {}
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
}
