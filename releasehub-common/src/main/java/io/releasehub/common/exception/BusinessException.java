package io.releasehub.common.exception;

/**
 * 业务规则异常 (HTTP 400)
 * <p>
 * 用于违反业务规则的场景，如状态转换错误、操作被拒绝等
 */
public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    // ========== 静态工厂方法 ==========

    public static BusinessException of(ErrorCode errorCode, Object... args) {
        return new BusinessException(errorCode, args);
    }

    // ========== ReleaseWindow ==========

    public static BusinessException rwAlreadyFrozen() {
        return of(ErrorCode.RW_ALREADY_FROZEN);
    }

    public static BusinessException rwInvalidTimeRange() {
        return of(ErrorCode.RW_INVALID_TIME_RANGE);
    }

    public static BusinessException rwTimeRequired() {
        return of(ErrorCode.RW_TIME_REQUIRED);
    }

    public static BusinessException rwInvalidState(Object currentState) {
        return of(ErrorCode.RW_INVALID_STATE, currentState);
    }

    public static BusinessException rwNotConfigured() {
        return of(ErrorCode.RW_NOT_CONFIGURED);
    }

    public static BusinessException rwNoIterations(Object windowId) {
        return of(ErrorCode.RW_NO_ITERATIONS, windowId);
    }

    // ========== Group ==========

    public static BusinessException groupCodeExists(Object code) {
        return of(ErrorCode.GROUP_CODE_EXISTS, code);
    }

    public static BusinessException groupHasChildren(Object code) {
        return of(ErrorCode.GROUP_HAS_CHILDREN, code);
    }

    public static BusinessException groupParentSelf() {
        return of(ErrorCode.GROUP_PARENT_SELF);
    }

    public static BusinessException groupReferenced(Object code) {
        return of(ErrorCode.GROUP_REFERENCED, code);
    }

    // ========== Iteration ==========

    public static BusinessException iterationAttached(Object key) {
        return of(ErrorCode.ITERATION_ATTACHED, key);
    }

    // ========== Version Policy ==========

    public static BusinessException vpCustomNotSupported() {
        return of(ErrorCode.VERSION_CUSTOM_NOT_SUPPORTED);
    }

    public static BusinessException versionNotFoundInFile() {
        return of(ErrorCode.VERSION_NOT_FOUND_IN_FILE);
    }

    // ========== GitLab ==========

    public static BusinessException gitlabSettingsMissing() {
        return of(ErrorCode.GITLAB_SETTINGS_MISSING);
    }

    // ========== Repository ==========

    public static BusinessException repoAttached(Object repoId) {
        return of(ErrorCode.REPO_ATTACHED, repoId);
    }

    // ========== Run Task ==========

    public static BusinessException runTaskNotRetryable(Object status) {
        return of(ErrorCode.RUN_TASK_NOT_RETRYABLE, status);
    }

    public static BusinessException runTaskTagCreateFailed(Object tagName) {
        return of(ErrorCode.RUN_TASK_TAG_CREATE_FAILED, tagName);
    }

    public static BusinessException runTaskMergeConflict(Object details) {
        return of(ErrorCode.RUN_TASK_MERGE_CONFLICT, details);
    }

    public static BusinessException runTaskMergeFailed(Object details) {
        return of(ErrorCode.RUN_TASK_MERGE_FAILED, details);
    }

    public static BusinessException runTaskContextNotFound(Object taskId) {
        return of(ErrorCode.RUN_TASK_CONTEXT_NOT_FOUND, taskId);
    }

    public static BusinessException runTaskCiTriggerFailed(Object details) {
        return of(ErrorCode.RUN_TASK_CI_TRIGGER_FAILED, details);
    }
}
