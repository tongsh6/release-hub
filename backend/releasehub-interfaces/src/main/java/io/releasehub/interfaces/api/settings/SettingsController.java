package io.releasehub.interfaces.api.settings;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "系统设置 - gitlab 设置")
public class SettingsController {
    private final SettingsPort settingsPort;
    private final GitLabPort gitLabPort;

    @GetMapping("/gitlab")
    @Operation(summary = "Get GitLab settings")
    public ApiResponse<Object> getGitLab() {
        return ApiResponse.success(settingsPort.getGitLab()
                .map(s -> new GitLabRequest(s.baseUrl(), mask(s.token())))
                .orElse(null));
    }

    @PostMapping("/gitlab")
    @Operation(summary = "Save GitLab settings")
    public ApiResponse<Boolean> saveGitLab(@RequestBody GitLabRequest req) {
        settingsPort.saveGitLab(new SettingsPort.SettingsGitLab(req.getBaseUrl(), req.getToken()));
        return ApiResponse.success(true);
    }

    private String mask(String token) {
        if (token == null) return null;
        int n = token.length();
        return n <= 4 ? "****" : token.substring(0, 2) + "****" + token.substring(n - 2);
    }

    @GetMapping("/gitlab/test")
    @Operation(summary = "Test GitLab settings")
    public ApiResponse<Boolean> testGitLab() {
        return ApiResponse.success(gitLabPort.testConnection());
    }

    @GetMapping("/naming")
    @Operation(summary = "Get naming settings")
    public ApiResponse<Object> getNaming() {
        return ApiResponse.success(settingsPort.getNaming().orElse(null));
    }

    @PostMapping("/naming")
    @Operation(summary = "Save naming settings")
    public ApiResponse<Boolean> saveNaming(@RequestBody NamingRequest req) {
        settingsPort.saveNaming(new SettingsPort.SettingsNaming(req.getFeatureTemplate(), req.getReleaseTemplate()));
        return ApiResponse.success(true);
    }

    @GetMapping("/ref")
    @Operation(summary = "Get ref settings")
    public ApiResponse<Object> getRef() {
        return ApiResponse.success(settingsPort.getRef().orElse(null));
    }

    @PostMapping("/ref")
    @Operation(summary = "Save ref settings")
    public ApiResponse<Boolean> saveRef() {
        settingsPort.saveRef(new SettingsPort.SettingsRef());
        return ApiResponse.success(true);
    }

    @GetMapping("/blocking")
    @Operation(summary = "Get blocking settings")
    public ApiResponse<Object> getBlocking() {
        return ApiResponse.success(settingsPort.getBlocking().orElse(null));
    }

    @PostMapping("/blocking")
    @Operation(summary = "Save blocking settings")
    public ApiResponse<Boolean> saveBlocking(@RequestBody BlockingRequest req) {
        settingsPort.saveBlocking(new SettingsPort.SettingsBlocking(req.getDefaultPolicy()));
        return ApiResponse.success(true);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GitLabRequest {
        private String baseUrl;
        private String token;
    }

    @Data
    public static class NamingRequest {
        private String featureTemplate;
        private String releaseTemplate;
    }

    @Data
    public static class BlockingRequest {
        private String defaultPolicy;
    }
}
