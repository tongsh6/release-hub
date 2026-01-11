package io.releasehub.application.auth;

import io.releasehub.domain.user.User;

public interface TokenPort {
    TokenInfo createToken(User user);
    boolean validateToken(String token);
    String getUsernameFromToken(String token);
}
