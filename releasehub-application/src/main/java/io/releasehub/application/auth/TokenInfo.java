package io.releasehub.application.auth;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenInfo {
    private String token;
    private Instant expiresAt;
}
