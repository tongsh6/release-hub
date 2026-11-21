package io.releasehub.application.port.out;

import java.util.Objects;
import java.util.Optional;

/** GitLab 文件读取与基于原始 revision 的条件写入端口。 */
public interface GitLabFilePort {

    /** 内容与读取该内容时的 revision 必须来自同一份远端响应。 */
    record FileSnapshot(String content, String lastCommitId) {
        public FileSnapshot {
            Objects.requireNonNull(content, "content");
            if (lastCommitId == null || !lastCommitId.matches("[0-9a-fA-F]{40,64}")) {
                throw new IllegalArgumentException("A file snapshot requires its original commit revision");
            }
        }
    }

    /** 读取文件内容和原始 revision；文件不存在时返回空。 */
    Optional<FileSnapshot> readFile(String repoCloneUrl, String branch, String filePath);

    boolean fileExists(String repoCloneUrl, String branch, String filePath);

    /**
     * 仅在文件仍处于生成新内容所依据的 revision 时更新。
     * 禁止重新读取最新 revision 后替换 expected，从而覆盖并发修改。
     */
    boolean updateFile(String repoCloneUrl, String branch, String filePath,
                       FileSnapshot expected, String content, String commitMessage);
}
