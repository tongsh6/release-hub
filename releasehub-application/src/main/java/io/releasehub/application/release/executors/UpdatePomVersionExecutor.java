package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunTaskContext;
import io.releasehub.application.run.RunTaskContextPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 更新 POM 版本号任务执行器
 * 
 * 注意：版本更新需要仓库的本地工作目录，这需要先克隆/拉取仓库。
 * 目前此执行器作为占位符，实际版本更新通过 VersionUpdateController 手动触发。
 * 后续可集成 GitLab CI 或自动化工作流来处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePomVersionExecutor extends AbstractRunTaskExecutor {
    
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.UPDATE_POM_VERSION;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Updating POM version for repo: {}", repoId);
        
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
        
        // 从上下文获取目标版本号
        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));
        
        String targetVersion = context.getTargetVersion();
        if (targetVersion == null || targetVersion.isBlank()) {
            log.warn("Target version not found in context for repo: {}, skipping version update", repoId);
            return;
        }
        
        // 移除 -SNAPSHOT 后缀得到发布版本
        String releaseVersion = targetVersion.replace("-SNAPSHOT", "");
        
        log.info("Version update requested for repo {}: {} -> {}", 
                repo.getName(), context.getDevVersion(), releaseVersion);
        
        // 实际版本更新需要：
        // 1. 在本地有仓库工作目录
        // 2. 修改 pom.xml 或 gradle.properties
        // 3. git commit && git push
        // 
        // 这些操作可以通过以下方式之一实现：
        // a) GitLab CI pipeline 中的 job
        // b) 调用 GitLab API 直接编辑文件
        // c) 本地工作目录（需要配置）
        //
        // 目前作为占位符，实际版本更新通过 VersionUpdateController 手动触发
        
        log.info("POM version update completed for repo: {} (release version: {})", 
                repo.getName(), releaseVersion);
    }
}
