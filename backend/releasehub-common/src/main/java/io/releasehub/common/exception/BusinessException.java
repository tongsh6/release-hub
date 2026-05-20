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

    public static BusinessException rwIterationGroupMismatch(Object iterationKey, Object windowGroup, Object iterationGroup) {
        return of(ErrorCode.RW_ITERATION_GROUP_MISMATCH, iterationKey, windowGroup, iterationGroup);
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

    public static BusinessException groupNotLeaf(Object code) {
        return of(ErrorCode.GROUP_NOT_LEAF, code);
    }

    // ========== Iteration ==========

    public static BusinessException iterationAttached(Object key) {
        return of(ErrorCode.ITERATION_ATTACHED, key);
    }

    public static BusinessException iterationRepoGroupMismatch(Object iterationKey, Object repoId, Object iterationGroup, Object repoGroup) {
        return of(ErrorCode.ITERATION_REPO_GROUP_MISMATCH, iterationKey, repoId, iterationGroup, repoGroup);
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

    public static BusinessException gitlabConnectionFailed(Object reason) {
        return of(ErrorCode.GITLAB_CONNECTION_FAILED, reason);
    }

    // ========== Repository ==========

    public static BusinessException repoAttached(Object repoId) {
        return of(ErrorCode.REPO_ATTACHED, repoId);
    }

    // ========== Run ==========

    public static BusinessException runNoItemsCreated(int iterationCount, Object windowId) {
        return of(ErrorCode.RUN_NO_ITEMS_CREATED, iterationCount, windowId);
    }

    // ========== Conflict Detection ==========

    public static BusinessException conflictDetected(String detail) {
        return of(ErrorCode.CONFLICT_DETECTED, detail);
    }
}
