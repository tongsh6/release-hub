package io.releasehub.common.exception;

/**
 * 认证异常 (HTTP 401)
 * <p>
 * 用于身份验证失败的场景
 */
public class AuthenticationException extends BaseException {

    public AuthenticationException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    // ========== 静态工厂方法 ==========

    public static AuthenticationException of(ErrorCode errorCode, Object... args) {
        return new AuthenticationException(errorCode, args);
    }

    // ========== 快捷方法 ==========

    public static AuthenticationException failed() {
        return of(ErrorCode.AUTH_FAILED);
    }

    public static AuthenticationException tokenExpired() {
        return of(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    public static AuthenticationException tokenInvalid() {
        return of(ErrorCode.AUTH_TOKEN_INVALID);
    }

    public static AuthenticationException required() {
        return of(ErrorCode.AUTH_REQUIRED);
    }
}
