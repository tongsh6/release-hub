package io.releasehub.application.version;

import io.releasehub.domain.version.BuildTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 版本更新应用服务
 * <p>
 * 负责协调版本更新操作，根据构建工具类型选择合适的 VersionUpdater 实现。
 */
@Service
@RequiredArgsConstructor
public class VersionUpdateAppService {

    private final List<VersionUpdater> versionUpdaters;

    /**
     * 执行版本更新
     *
     * @param request 版本更新请求
     * @return 版本更新结果
     */
    public VersionUpdateResult updateVersion(VersionUpdateRequest request) {
        BuildTool buildTool = request.buildTool();
        
        VersionUpdater updater = findUpdater(buildTool);
        if (updater == null) {
            return VersionUpdateResult.failure(
                    "No VersionUpdater found for build tool: " + buildTool,
                    request.repoPath()
            );
        }
        
        return updater.update(request);
    }

    /**
     * 查找支持指定构建工具的 VersionUpdater
     */
    private VersionUpdater findUpdater(BuildTool buildTool) {
        return versionUpdaters.stream()
                .filter(updater -> updater.supports(buildTool))
                .findFirst()
                .orElse(null);
    }
}
