package io.releasehub.common.exception;

/**
 * 参数校验异常 (HTTP 400)
 * <p>
 * 用于字段校验失败、格式错误等场景
 */
public class ValidationException extends BaseException {

    public ValidationException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    // ========== 静态工厂方法 ==========

    public static ValidationException of(ErrorCode errorCode, Object... args) {
        return new ValidationException(errorCode, args);
    }

    // ========== 通用快捷方法 ==========

    public static ValidationException invalidParameter(Object paramName) {
        return of(ErrorCode.INVALID_PARAMETER, paramName);
    }

    // ========== ReleaseWindow ==========

    public static ValidationException rwKeyRequired() {
        return of(ErrorCode.RW_KEY_REQUIRED);
    }

    public static ValidationException rwKeyTooLong(int maxLength) {
        return of(ErrorCode.RW_KEY_TOO_LONG, maxLength);
    }

    public static ValidationException rwNameRequired() {
        return of(ErrorCode.RW_NAME_REQUIRED);
    }

    public static ValidationException rwNameTooLong(int maxLength) {
        return of(ErrorCode.RW_NAME_TOO_LONG, maxLength);
    }

    // ========== Repository ==========

    public static ValidationException repoNameRequired() {
        return of(ErrorCode.REPO_NAME_REQUIRED);
    }

    public static ValidationException repoNameTooLong(int maxLength) {
        return of(ErrorCode.REPO_NAME_TOO_LONG, maxLength);
    }

    public static ValidationException repoProjectRequired() {
        return of(ErrorCode.REPO_PROJECT_REQUIRED);
    }

    public static ValidationException repoGitlabIdRequired() {
        return of(ErrorCode.REPO_GITLAB_ID_REQUIRED);
    }

    public static ValidationException repoUrlRequired() {
        return of(ErrorCode.REPO_URL_REQUIRED);
    }

    public static ValidationException repoUrlTooLong(int maxLength) {
        return of(ErrorCode.REPO_URL_TOO_LONG, maxLength);
    }

    public static ValidationException repoBranchRequired() {
        return of(ErrorCode.REPO_BRANCH_REQUIRED);
    }

    public static ValidationException repoBranchTooLong(int maxLength) {
        return of(ErrorCode.REPO_BRANCH_TOO_LONG, maxLength);
    }

    // ========== Group ==========

    public static ValidationException groupNameRequired() {
        return of(ErrorCode.GROUP_NAME_REQUIRED);
    }

    public static ValidationException groupNameTooLong(int maxLength) {
        return of(ErrorCode.GROUP_NAME_TOO_LONG, maxLength);
    }

    public static ValidationException groupCodeRequired() {
        return of(ErrorCode.GROUP_CODE_REQUIRED);
    }

    public static ValidationException groupCodeTooLong(int maxLength) {
        return of(ErrorCode.GROUP_CODE_TOO_LONG, maxLength);
    }

    public static ValidationException groupParentTooLong(int maxLength) {
        return of(ErrorCode.GROUP_PARENT_TOO_LONG, maxLength);
    }

    public static ValidationException groupIdInvalid() {
        return of(ErrorCode.GROUP_ID_INVALID);
    }

    // ========== VersionPolicy ==========

    public static ValidationException vpNameRequired() {
        return of(ErrorCode.VERSION_POLICY_NAME_REQUIRED);
    }

    public static ValidationException vpNameTooLong(int maxLength) {
        return of(ErrorCode.VERSION_POLICY_NAME_TOO_LONG, maxLength);
    }

    public static ValidationException vpInvalidFormat(String version) {
        return of(ErrorCode.VERSION_INVALID_FORMAT, version);
    }

    public static ValidationException vpCurrentVersionRequired() {
        return of(ErrorCode.VERSION_CURRENT_REQUIRED);
    }

    // ========== Project ==========

    public static ValidationException projectNameRequired() {
        return of(ErrorCode.PROJECT_NAME_REQUIRED);
    }

    public static ValidationException projectNameTooLong(int maxLength) {
        return of(ErrorCode.PROJECT_NAME_TOO_LONG, maxLength);
    }

    public static ValidationException projectDescTooLong(int maxLength) {
        return of(ErrorCode.PROJECT_DESC_TOO_LONG, maxLength);
    }
}
