package io.releasehub.application.run;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunAppService {
    private final RunPort runPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final VersionUpdateAppService versionUpdateAppService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Run retry(String runId, List<String> items, String operator) {
        var previous = runPort.findById(runId).orElseThrow();
        Run run = Run.start(previous.getRunType(), operator, Instant.now(clock));
        previous.getItems().stream()
                .filter(i -> items.stream().anyMatch(sel -> sel.equals(i.getWindowKey() + "::" + i.getRepo().value() + "::" + i.getIterationKey().value())))
                .filter(i -> i.getFinalResult() == RunItemResult.FAILED || i.getFinalResult() == RunItemResult.MERGE_BLOCKED)
                .forEach(i -> {
                    RunItem item = RunItem.create(i.getWindowKey(), i.getRepo(), i.getIterationKey(), i.getPlannedOrder(), Instant.now(clock));
                    int seq = item.getPlannedOrder();
                    Instant s1 = Instant.now(clock);
                    item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "retry"));
                    Instant s2 = Instant.now(clock);
                    item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.SKIPPED, s2, s2, "retry"));
                    Instant s3 = Instant.now(clock);
                    item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.SKIPPED, s3, s3, "retry"));
                    Instant s4 = Instant.now(clock);
                    item.addStep(new RunStep(ActionType.TRY_MERGE, RunItemResult.MERGE_BLOCKED, s4, s4, "blocked"));
                    item.setExecutedOrder(seq);
                    item.finishWith(RunItemResult.MERGE_BLOCKED, Instant.now(clock));
                    run.addItem(item);
                });
        run.finish(Instant.now(clock));
        runPort.save(run);
        return run;
    }
    @Transactional
    public Run startOrchestrate(String windowId, List<String> repoIds, List<String> iterationKeys, boolean failFast, String operator) {
        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, Instant.now(clock));
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(windowId)).orElseThrow();
        List<WindowIteration> bindings = windowIterationPort.listByWindow(new ReleaseWindowId(windowId));
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));
        List<IterationKey> orderedIterations = bindings.stream().map(WindowIteration::getIterationKey).distinct().toList();

        for (String repoIdStr : repoIds) {
            RepoId repoId = new RepoId(repoIdStr);
            for (IterationKey ik : orderedIterations) {
                if (!iterationKeys.isEmpty() && iterationKeys.stream().noneMatch(k -> k.equals(ik.value()))) {
                    continue;
                }
                Iteration it = iterationPort.findByKey(ik).orElseThrow();
                if (it.getRepos().stream().noneMatch(r -> r.equals(repoId))) {
                    continue;
                }
                RunItem item = RunItem.create(rw.getName(), repoId, ik, orderedIterations.indexOf(ik) + 1, Instant.now(clock));
                int seq = item.getPlannedOrder();
                Instant s1 = Instant.now(clock);
                item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "dry"));
                Instant s2 = Instant.now(clock);
                item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.SKIPPED, s2, s2, "dry"));
                Instant s3 = Instant.now(clock);
                item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.SKIPPED, s3, s3, "dry"));
                Instant s4 = Instant.now(clock);
                item.addStep(new RunStep(ActionType.TRY_MERGE, RunItemResult.MERGE_BLOCKED, s4, s4, "blocked"));
                item.setExecutedOrder(seq);
                item.finishWith(RunItemResult.MERGE_BLOCKED, Instant.now(clock));
                run.addItem(item);
                if (failFast) {
                    break;
                }
            }
        }
        run.finish(Instant.now(clock));
        runPort.save(run);
        return run;
    }

    /**
     * 执行版本更新
     *
     * @param windowId 发布窗口 ID
     * @param repoId 仓库 ID
     * @param targetVersion 目标版本号
     * @param buildTool 构建工具类型
     * @param repoPath 仓库路径（本地文件系统路径）
     * @param pomPath Maven pom.xml 路径（可选，Maven 时使用）
     * @param gradlePropertiesPath Gradle properties 路径（可选，Gradle 时使用）
     * @param operator 操作者
     * @return 执行记录
     */
    @Transactional
    public Run executeVersionUpdate(
            String windowId,
            String repoId,
            String targetVersion,
            BuildTool buildTool,
            String repoPath,
            String pomPath,
            String gradlePropertiesPath,
            String operator
    ) {
        Instant now = Instant.now(clock);
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);
        
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
        
        // 验证仓库存在
        codeRepositoryPort.findById(new RepoId(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
        
        // 创建版本更新请求
        VersionUpdateRequest request = buildTool == BuildTool.MAVEN
                ? VersionUpdateRequest.forMaven(new RepoId(repoId), repoPath, targetVersion, pomPath)
                : VersionUpdateRequest.forGradle(new RepoId(repoId), repoPath, targetVersion, gradlePropertiesPath);
        
        // 执行版本更新
        Instant stepStart = Instant.now(clock);
        VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
        Instant stepEnd = Instant.now(clock);
        
        // 创建 RunItem（版本更新不关联迭代，使用虚拟迭代 key）
        IterationKey dummyIterationKey = new IterationKey("VERSION_UPDATE");
        RunItem item = RunItem.create(rw.getName(), new RepoId(repoId), dummyIterationKey, 1, now);
        
        // 创建执行步骤
        RunItemResult stepResult = result.success()
                ? RunItemResult.VERSION_UPDATE_SUCCESS
                : RunItemResult.VERSION_UPDATE_FAILED;
        
        // 构建步骤消息，包含版本信息和 diff
        // 数据库字段已更新为 TEXT，可以存储完整的 diff 信息
        String stepMessage;
        if (result.success()) {
            String baseMessage = String.format("Version updated from %s to %s. File: %s", 
                    result.oldVersion(), result.newVersion(), result.filePath());
            // 如果 diff 存在，添加到消息中（使用统一的 "--- Diff ---" 格式）
            if (result.diff() != null && !result.diff().isBlank()) {
                stepMessage = baseMessage + "\n--- Diff ---\n" + result.diff();
            } else {
                stepMessage = baseMessage;
            }
        } else {
            stepMessage = result.errorMessage();
        }
        
        RunStep step = new RunStep(ActionType.UPDATE_VERSION, stepResult, stepStart, stepEnd, stepMessage);
        item.addStep(step);
        item.setExecutedOrder(1);
        item.finishWith(stepResult, stepEnd);
        
        run.addItem(item);
        run.finish(stepEnd);
        runPort.save(run);
        
        return run;
    }

    /**
     * 批量执行版本更新
     *
     * @param windowId 发布窗口 ID
     * @param repositories 仓库更新列表
     * @param targetVersion 目标版本号
     * @param operator 操作者
     * @return 执行记录
     */
    @Transactional
    public Run executeBatchVersionUpdate(
            String windowId,
            List<RepoVersionUpdateInfo> repositories,
            String targetVersion,
            String operator
    ) {
        Instant now = Instant.now(clock);
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);
        
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));

        int order = 1;
        for (RepoVersionUpdateInfo repoInfo : repositories) {
            // 验证仓库存在
            codeRepositoryPort.findById(new RepoId(repoInfo.repoId()))
                    .orElseThrow(() -> NotFoundException.repository(repoInfo.repoId()));

            // 创建版本更新请求
            VersionUpdateRequest request = repoInfo.buildTool() == BuildTool.MAVEN
                    ? VersionUpdateRequest.forMaven(new RepoId(repoInfo.repoId()), repoInfo.repoPath(), targetVersion, repoInfo.pomPath())
                    : VersionUpdateRequest.forGradle(new RepoId(repoInfo.repoId()), repoInfo.repoPath(), targetVersion, repoInfo.gradlePropertiesPath());

            // 执行版本更新
            Instant stepStart = Instant.now(clock);
            VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
            Instant stepEnd = Instant.now(clock);

            // 创建 RunItem（版本更新不关联迭代，使用虚拟迭代 key）
            IterationKey dummyIterationKey = new IterationKey("VERSION_UPDATE");
            RunItem item = RunItem.create(rw.getName(), new RepoId(repoInfo.repoId()), dummyIterationKey, order, now);

            // 创建执行步骤
            RunItemResult stepResult = result.success()
                    ? RunItemResult.VERSION_UPDATE_SUCCESS
                    : RunItemResult.VERSION_UPDATE_FAILED;

            // 构建步骤消息，包含版本信息和 diff
            String stepMessage;
            if (result.success()) {
                String baseMessage = String.format("Version updated from %s to %s. File: %s", 
                        result.oldVersion(), result.newVersion(), result.filePath());
                // 如果 diff 存在，添加到消息中
                if (result.diff() != null && !result.diff().isBlank()) {
                    stepMessage = baseMessage + "\n--- Diff ---\n" + result.diff();
                } else {
                    stepMessage = baseMessage;
                }
            } else {
                stepMessage = result.errorMessage();
            }

            RunStep step = new RunStep(ActionType.UPDATE_VERSION, stepResult, stepStart, stepEnd, stepMessage);
            item.addStep(step);
            item.setExecutedOrder(order);
            item.finishWith(stepResult, stepEnd);

            run.addItem(item);
            order++;
        }

        run.finish(Instant.now(clock));
        runPort.save(run);

        return run;
    }

    /**
     * 仓库版本更新信息
     */
    public record RepoVersionUpdateInfo(
            String repoId,
            BuildTool buildTool,
            String repoPath,
            String pomPath,
            String gradlePropertiesPath
    ) {}
}
