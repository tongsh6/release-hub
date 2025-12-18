package io.releasehub.application.auth;

public interface PasswordPort {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
