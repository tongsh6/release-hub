package io.releasehub.common.exception;

/**
 * 资源不存在异常 (HTTP 404)
 */
public class NotFoundException extends BaseException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    // ========== 静态工厂方法 ==========

    public static NotFoundException of(ErrorCode errorCode, Object... args) {
        return new NotFoundException(errorCode, args);
    }

    // ========== 快捷方法 ==========

    public static NotFoundException releaseWindow(Object id) {
        return of(ErrorCode.RW_NOT_FOUND, id);
    }

    public static NotFoundException repository(Object id) {
        return of(ErrorCode.REPO_NOT_FOUND, id);
    }

    public static NotFoundException group(Object id) {
        return of(ErrorCode.GROUP_NOT_FOUND, id);
    }

    public static NotFoundException groupCode(Object code) {
        return of(ErrorCode.GROUP_CODE_NOT_FOUND, code);
    }

    public static NotFoundException groupParent(Object code) {
        return of(ErrorCode.GROUP_PARENT_NOT_FOUND, code);
    }

    public static NotFoundException iteration(Object key) {
        return of(ErrorCode.ITERATION_NOT_FOUND, key);
    }

    public static NotFoundException versionPolicy(Object id) {
        return of(ErrorCode.VERSION_POLICY_NOT_FOUND, id);
    }

    public static NotFoundException project(Object id) {
        return of(ErrorCode.PROJECT_NOT_FOUND, id);
    }

    public static NotFoundException run(Object id) {
        return of(ErrorCode.RUN_NOT_FOUND, id);
    }
}
