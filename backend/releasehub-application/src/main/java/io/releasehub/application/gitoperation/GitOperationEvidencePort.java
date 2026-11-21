package io.releasehub.application.gitoperation;

/** 只允许读取远端证据，不允许通过创建/关闭 MR 等探测来核对。 */
public interface GitOperationEvidencePort {
    GitReadResult<String> read(GitOperationJournalPort.Detail detail);
}
