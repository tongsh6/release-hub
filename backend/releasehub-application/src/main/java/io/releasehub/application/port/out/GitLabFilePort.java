package io.releasehub.application.port.out;

import java.util.Optional;

/**
 * GitLab 文件读取端口
 */
public interface GitLabFilePort {
    
    /**
     * 从 GitLab 仓库读取文件内容
     * @param repoCloneUrl 仓库克隆地址
     * @param branch 分支名
     * @param filePath 文件路径
     * @return 文件内容，如果文件不存在则返回空
     */
    Optional<String> readFile(String repoCloneUrl, String branch, String filePath);
    
    /**
     * 检查文件是否存在
     * @param repoCloneUrl 仓库克隆地址
     * @param branch 分支名
     * @param filePath 文件路径
     * @return 是否存在
     */
    boolean fileExists(String repoCloneUrl, String branch, String filePath);
}
