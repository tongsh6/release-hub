package io.releasehub.application.gitoperation;

import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import java.util.List;

/** 绑定到单一远端仓库。查询失败不能证明不存在；每个写方法最多发送一次写请求。 */
public interface GitMergeRequestPort {
    enum State { OPENED, MERGED, CLOSED, UNKNOWN }
    enum Readiness { MERGEABLE, PENDING, BLOCKED, UNKNOWN }

    record SourceRevision(String projectId, String sha) {}

    record MergeRequest(String id, String url, String projectId, String sourceBranch, String targetBranch,
                        String sourceSha, String mergeSha, State state, Readiness readiness,
                        MergeBlockReason blockReason, String detail) {
        public MergeRequest withReadiness(Readiness value, MergeBlockReason reason, String message) {
            return new MergeRequest(id, url, projectId, sourceBranch, targetBranch, sourceSha, mergeSha,
                    state, value, reason, message);
        }
    }

    GitReadResult<SourceRevision> readSource(String source);
    GitReadResult<List<MergeRequest>> find(String source, String target, State state);
    /** 确认当前 target 分支包含指定的不可变 source SHA；查询不完整时不能返回 false。 */
    GitReadResult<Boolean> contains(String target, String sourceSha);
    GitReadResult<MergeRequest> read(String id);
    /** expectedSourceSha is recorded as intent evidence; GitLab create does not offer an atomic SHA precondition. */
    GitWriteResult<MergeRequest> create(String source, String target, String expectedSourceSha, String title);
    GitWriteResult<MergeRequest> merge(String id, String sourceSha, String message);
}
