package io.releasehub.common.exception;

/**
 * 权限不足异常 (HTTP 403)
 * <p>
 * 用于已认证但无权限访问的场景
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    // ========== 静态工厂方法 ==========

    public static ForbiddenException of(ErrorCode errorCode, Object... args) {
        return new ForbiddenException(errorCode, args);
    }

    // ========== 快捷方法 ==========

    public static ForbiddenException userDisabled() {
        return of(ErrorCode.AUTH_USER_DISABLED);
    }

    public static ForbiddenException accessDenied() {
        return of(ErrorCode.AUTH_ACCESS_DENIED);
    }
}
