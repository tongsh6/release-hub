package io.releasehub.application.auth;

import io.releasehub.domain.user.User;

public interface TokenPort {
    TokenInfo createToken(User user);

    /**
     * Create token with extended expiration if rememberMe is true
     */
    TokenInfo createToken(User user, boolean rememberMe);

    boolean validateToken(String token);
    String getUsernameFromToken(String token);
}
