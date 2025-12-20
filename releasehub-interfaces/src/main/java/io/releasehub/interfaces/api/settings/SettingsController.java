package io.releasehub.interfaces.api.settings;

import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {
    private final SettingsPort settingsPort;

    @GetMapping("/gitlab")
    public ApiResponse<Object> getGitLab() {
        return ApiResponse.success(settingsPort.getGitLab().orElse(null));
    }

    @PostMapping("/gitlab")
    public ApiResponse<Boolean> saveGitLab(@RequestBody GitLabRequest req) {
        settingsPort.saveGitLab(new SettingsPort.SettingsGitLab(req.getBaseUrl(), mask(req.getToken())));
        return ApiResponse.success(true);
    }

    private String mask(String token) {
        if (token == null) return null;
        int n = token.length();
        return n <= 4 ? "****" : token.substring(0, 2) + "****" + token.substring(n - 2);
    }

    @GetMapping("/gitlab/test")
    public ApiResponse<Boolean> testGitLab() {
        return ApiResponse.success(true);
    }

    @GetMapping("/naming")
    public ApiResponse<Object> getNaming() {
        return ApiResponse.success(settingsPort.getNaming().orElse(null));
    }

    @PostMapping("/naming")
    public ApiResponse<Boolean> saveNaming(@RequestBody NamingRequest req) {
        settingsPort.saveNaming(new SettingsPort.SettingsNaming(req.getFeatureTemplate(), req.getReleaseTemplate()));
        return ApiResponse.success(true);
    }

    @GetMapping("/ref")
    public ApiResponse<Object> getRef() {
        return ApiResponse.success(settingsPort.getRef().orElse(null));
    }

    @PostMapping("/ref")
    public ApiResponse<Boolean> saveRef() {
        settingsPort.saveRef(new SettingsPort.SettingsRef());
        return ApiResponse.success(true);
    }

    @GetMapping("/blocking")
    public ApiResponse<Object> getBlocking() {
        return ApiResponse.success(settingsPort.getBlocking().orElse(null));
    }

    @PostMapping("/blocking")
    public ApiResponse<Boolean> saveBlocking(@RequestBody BlockingRequest req) {
        settingsPort.saveBlocking(new SettingsPort.SettingsBlocking(req.getDefaultPolicy()));
        return ApiResponse.success(true);
    }

    @Data
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
