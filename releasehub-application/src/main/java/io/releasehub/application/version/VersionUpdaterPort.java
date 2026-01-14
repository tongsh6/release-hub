package io.releasehub.application.version;

import io.releasehub.domain.version.BuildTool;

/**
 * 版本更新器 Port 接口
 * <p>
 * 定义版本更新的能力，支持多种构建工具（Maven、Gradle）的可插拔实现。
 * 实现类应在 infrastructure 层。
 */
public interface VersionUpdaterPort {
    /**
     * 执行版本更新
     *
     * @param request 版本更新请求
     * @return 版本更新结果
     */
    VersionUpdateResult update(VersionUpdateRequest request);

    /**
     * 检查是否支持指定的构建工具
     *
     * @param buildTool 构建工具类型
     * @return 是否支持
     */
    boolean supports(BuildTool buildTool);
}
