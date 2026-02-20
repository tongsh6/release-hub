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
    AUTH_REQUIRED("AUTH_006", "error.auth.required", 401),

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
    RW_ID_INVALID("RW_011", "error.rw.id_invalid", 400),
    RW_NO_ITERATIONS("RW_012", "error.rw.no_iterations", 400),

    // ========== 代码仓库 (REPO) ==========
    REPO_NOT_FOUND("REPO_001", "error.repo.not_found", 404),
    REPO_NAME_REQUIRED("REPO_002", "error.repo.name_required", 400),
    REPO_NAME_TOO_LONG("REPO_003", "error.repo.name_too_long", 400),
    REPO_URL_REQUIRED("REPO_006", "error.repo.url_required", 400),
    REPO_URL_TOO_LONG("REPO_007", "error.repo.url_too_long", 400),
    REPO_BRANCH_REQUIRED("REPO_008", "error.repo.branch_required", 400),
    REPO_BRANCH_TOO_LONG("REPO_009", "error.repo.branch_too_long", 400),
    REPO_ID_INVALID("REPO_010", "error.repo.id_invalid", 400),
    REPO_ATTACHED("REPO_011", "error.repo.attached", 400),

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
    GROUP_REFERENCED("GROUP_013", "error.group.referenced", 400),
    GROUP_NOT_LEAF("GROUP_014", "error.group.not_leaf", 400),

    // ========== 迭代 (ITER) ==========
    ITERATION_NOT_FOUND("ITER_001", "error.iter.not_found", 404),
    ITERATION_ATTACHED("ITER_002", "error.iter.attached", 400),
    ITERATION_KEY_INVALID("ITER_003", "error.iter.key_invalid", 400),
    ITERATION_REPO_NOT_FOUND("ITER_004", "error.iter.repo_not_found", 404),

    // ========== 版本策略 (VP) ==========
    VERSION_POLICY_NOT_FOUND("VP_001", "error.vp.not_found", 404),
    VERSION_POLICY_NAME_REQUIRED("VP_002", "error.vp.name_required", 400),
    VERSION_POLICY_NAME_TOO_LONG("VP_003", "error.vp.name_too_long", 400),
    VERSION_INVALID_FORMAT("VP_004", "error.vp.invalid_format", 400),
    VERSION_CURRENT_REQUIRED("VP_005", "error.vp.current_version_required", 400),
    VERSION_CUSTOM_NOT_SUPPORTED("VP_006", "error.vp.custom_not_supported", 400),
    VERSION_NOT_FOUND_IN_FILE("VP_007", "error.vp.version_not_found_in_file", 400),
    VERSION_POLICY_ID_INVALID("VP_008", "error.vp.id_invalid", 400),

    // ========== 运行记录 (RUN) ==========
    RUN_NOT_FOUND("RUN_001", "error.run.not_found", 404),
    RUN_ID_INVALID("RUN_002", "error.run.id_invalid", 400),
    RUN_ITEM_ID_INVALID("RUN_003", "error.run.item_id_invalid", 400),
    RUN_TASK_NOT_FOUND("RUN_004", "error.run.task_not_found", 404),
    RUN_TASK_NOT_RETRYABLE("RUN_005", "error.run.task_not_retryable", 400),
    RUN_TASK_TAG_CREATE_FAILED("RUN_006", "error.run.task.tag_create_failed", 500),
    RUN_TASK_MERGE_CONFLICT("RUN_007", "error.run.task.merge_conflict", 409),
    RUN_TASK_MERGE_FAILED("RUN_008", "error.run.task.merge_failed", 500),
    RUN_TASK_CONTEXT_NOT_FOUND("RUN_009", "error.run.task.context_not_found", 500),
    RUN_TASK_CI_TRIGGER_FAILED("RUN_010", "error.run.task.ci_trigger_failed", 500),

    // ========== 窗口迭代关联 (WI) ==========
    WINDOW_ITERATION_ID_INVALID("WI_001", "error.wi.id_invalid", 400),

    // ========== GitLab (GITLAB) ==========
    GITLAB_SETTINGS_MISSING("GITLAB_001", "error.gitlab.settings_missing", 400),
    GITLAB_PROJECT_NOT_FOUND("GITLAB_002", "error.gitlab.project_not_found", 404),

    // ========== 分支规则 (BR) ==========
    BRANCH_RULE_NOT_FOUND("BR_001", "error.br.not_found", 404),
    BRANCH_RULE_NAME_REQUIRED("BR_002", "error.br.name_required", 400),
    BRANCH_RULE_NAME_TOO_LONG("BR_003", "error.br.name_too_long", 400),
    BRANCH_RULE_PATTERN_REQUIRED("BR_004", "error.br.pattern_required", 400),
    BRANCH_RULE_PATTERN_TOO_LONG("BR_005", "error.br.pattern_too_long", 400),
    BRANCH_RULE_PATTERN_INVALID("BR_006", "error.br.pattern_invalid", 400);

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
