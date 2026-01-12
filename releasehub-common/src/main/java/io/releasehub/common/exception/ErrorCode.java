package io.releasehub.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一错误码枚举
 * <p>
 * 命名规则：模块前缀_序号
 * 消息 key 规则：error.模块.具体错误
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 通用错误 (COMMON) ==========
    INVALID_PARAMETER("COMMON_001", "error.common.invalid_parameter", 400),
    RESOURCE_NOT_FOUND("COMMON_002", "error.common.resource_not_found", 404),
    INTERNAL_ERROR("COMMON_500", "error.common.internal", 500),

    // ========== 认证授权 (AUTH) ==========
    AUTH_FAILED("AUTH_001", "error.auth.failed", 401),
    AUTH_TOKEN_EXPIRED("AUTH_002", "error.auth.token_expired", 401),
    AUTH_TOKEN_INVALID("AUTH_003", "error.auth.token_invalid", 401),
    AUTH_USER_DISABLED("AUTH_004", "error.auth.user_disabled", 403),
    AUTH_ACCESS_DENIED("AUTH_005", "error.auth.access_denied", 403),

    // ========== 发布窗口 (RW) ==========
    RW_NOT_FOUND("RW_001", "error.rw.not_found", 404),
    RW_KEY_REQUIRED("RW_002", "error.rw.key_required", 400),
    RW_KEY_TOO_LONG("RW_003", "error.rw.key_too_long", 400),
    RW_NAME_REQUIRED("RW_004", "error.rw.name_required", 400),
    RW_NAME_TOO_LONG("RW_005", "error.rw.name_too_long", 400),
    RW_ALREADY_FROZEN("RW_006", "error.rw.already_frozen", 400),
    RW_INVALID_TIME_RANGE("RW_007", "error.rw.invalid_time_range", 400),
    RW_TIME_REQUIRED("RW_008", "error.rw.time_required", 400),
    RW_INVALID_STATE("RW_009", "error.rw.invalid_state", 400),
    RW_NOT_CONFIGURED("RW_010", "error.rw.not_configured", 400),

    // ========== 代码仓库 (REPO) ==========
    REPO_NOT_FOUND("REPO_001", "error.repo.not_found", 404),
    REPO_NAME_REQUIRED("REPO_002", "error.repo.name_required", 400),
    REPO_NAME_TOO_LONG("REPO_003", "error.repo.name_too_long", 400),
    REPO_PROJECT_REQUIRED("REPO_004", "error.repo.project_required", 400),
    REPO_GITLAB_ID_REQUIRED("REPO_005", "error.repo.gitlab_id_required", 400),
    REPO_URL_REQUIRED("REPO_006", "error.repo.url_required", 400),
    REPO_URL_TOO_LONG("REPO_007", "error.repo.url_too_long", 400),
    REPO_BRANCH_REQUIRED("REPO_008", "error.repo.branch_required", 400),
    REPO_BRANCH_TOO_LONG("REPO_009", "error.repo.branch_too_long", 400),

    // ========== 分组 (GROUP) ==========
    GROUP_NOT_FOUND("GROUP_001", "error.group.not_found", 404),
    GROUP_CODE_NOT_FOUND("GROUP_002", "error.group.code_not_found", 404),
    GROUP_NAME_REQUIRED("GROUP_003", "error.group.name_required", 400),
    GROUP_NAME_TOO_LONG("GROUP_004", "error.group.name_too_long", 400),
    GROUP_CODE_REQUIRED("GROUP_005", "error.group.code_required", 400),
    GROUP_CODE_TOO_LONG("GROUP_006", "error.group.code_too_long", 400),
    GROUP_CODE_EXISTS("GROUP_007", "error.group.code_exists", 409),
    GROUP_HAS_CHILDREN("GROUP_008", "error.group.has_children", 400),
    GROUP_PARENT_SELF("GROUP_009", "error.group.parent_self", 400),
    GROUP_PARENT_NOT_FOUND("GROUP_010", "error.group.parent_not_found", 404),
    GROUP_PARENT_TOO_LONG("GROUP_011", "error.group.parent_too_long", 400),
    GROUP_ID_INVALID("GROUP_012", "error.group.id_invalid", 400),

    // ========== 迭代 (ITER) ==========
    ITERATION_NOT_FOUND("ITER_001", "error.iter.not_found", 404),
    ITERATION_ATTACHED("ITER_002", "error.iter.attached", 400),

    // ========== 版本策略 (VP) ==========
    VERSION_POLICY_NOT_FOUND("VP_001", "error.vp.not_found", 404),
    VERSION_POLICY_NAME_REQUIRED("VP_002", "error.vp.name_required", 400),
    VERSION_POLICY_NAME_TOO_LONG("VP_003", "error.vp.name_too_long", 400),
    VERSION_INVALID_FORMAT("VP_004", "error.vp.invalid_format", 400),
    VERSION_CURRENT_REQUIRED("VP_005", "error.vp.current_version_required", 400),
    VERSION_CUSTOM_NOT_SUPPORTED("VP_006", "error.vp.custom_not_supported", 400),
    VERSION_NOT_FOUND_IN_FILE("VP_007", "error.vp.version_not_found_in_file", 400),

    // ========== 项目 (PJ) ==========
    PROJECT_NOT_FOUND("PJ_001", "error.pj.not_found", 404),
    PROJECT_NAME_REQUIRED("PJ_002", "error.pj.name_required", 400),
    PROJECT_NAME_TOO_LONG("PJ_003", "error.pj.name_too_long", 400),
    PROJECT_DESC_TOO_LONG("PJ_004", "error.pj.desc_too_long", 400),

    // ========== 运行记录 (RUN) ==========
    RUN_NOT_FOUND("RUN_001", "error.run.not_found", 404),

    // ========== GitLab (GITLAB) ==========
    GITLAB_SETTINGS_MISSING("GITLAB_001", "error.gitlab.settings_missing", 400);

    /**
     * 错误码
     */
    private final String code;

    /**
     * i18n 消息 key
     */
    private final String messageKey;

    /**
     * HTTP 状态码
     */
    private final int httpStatus;
}
