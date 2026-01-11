package io.releasehub.interfaces.auth;

import io.releasehub.application.auth.AuthAppService;
import io.releasehub.application.auth.TokenInfo;
import io.releasehub.application.user.UserPort;
import io.releasehub.domain.user.User;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "用户认证 - 认证管理")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAppService authAppService;
    private final UserPort userPort;

    @PostMapping("/auth/login")
    @Operation(summary = "Login")
    public ApiResponse<TokenInfo> login(@Valid @RequestBody LoginRequest request) {
        try {
            TokenInfo tokenInfo = authAppService.login(request.getUsername(), request.getPassword());
            return ApiResponse.success(tokenInfo);
        } catch (IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
            return ApiResponse.error("AUTH_FAILED", "Authentication failed");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ApiResponse.error("AUTH_REQUIRED", "Authentication required");
        }

        User user = userPort.findByUsername(userDetails.getUsername())
                            .orElseThrow(() -> new IllegalStateException("User not found"));

        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                Collections.emptyList() // MVP: No permissions yet
        );
        return ApiResponse.success(response);
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @RequiredArgsConstructor
    public static class UserResponse {
        private final String id;
        private final String username;
        private final String displayName;
        private final List<String> permissions;
    }
}
