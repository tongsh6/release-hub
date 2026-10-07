package io.releasehub.application.run.plan;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.window.ReleaseBranchNaming;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunPlanAppService {
    private static final List<String> STEPS = List.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE");
    private final RunPlanPort plans;
    private final RunPort runs;
    private final ReleaseWindowPort windows;
    private final WindowIterationPort bindings;
    private final IterationPort iterations;
    private final IterationRepoPort versions;
    private final CodeRepositoryPort repositories;
    private final SettingsPort settings;
    private final Clock clock;

    @Transactional
    public SavedRunPlan create(String windowId, List<String> repoIds, List<String> iterationKeys,
                               boolean failFast, String operator, String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid("幂等键需为 8 至 128 位字母、数字或 ._:-");
        if (operator == null || operator.isBlank()) throw invalid("缺少认证主体");
        List<String> repos = normalize(repoIds);
        List<String> selectedIterations = normalize(iterationKeys);
        if (repos.isEmpty()) throw invalid("请至少选择一个仓库");
        List<String> input = new ArrayList<>(List.of("LOCAL_PREVIEW_V1", windowId, Boolean.toString(failFast), Integer.toString(repos.size())));
        input.addAll(repos);
        input.add(Integer.toString(selectedIterations.size()));
        input.addAll(selectedIterations);
        String requestHash = PlanFingerprint.of(input);

        plans.lockRequest(operator, key);
        var existing = plans.findByRequest(operator, key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(requestHash)) throw BusinessException.of(ErrorCode.RUN_PLAN_KEY_CONFLICT);
            return existing.get();
        }

        var window = windows.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        if (window.getGroupCode() == null || window.getGroupCode().isBlank()) throw invalid("窗口缺少分组信息");
        if (window.getStatus() == ReleaseWindowStatus.CLOSED) throw invalid("已关闭窗口不能创建新计划预览");
        var allBindings = bindings.listByWindow(window.getId()).stream()
                .sorted(Comparator.comparing(WindowIteration::getAttachAt).thenComparing(b -> b.getIterationKey().value())).toList();
        Set<String> boundKeys = allBindings.stream().map(b -> b.getIterationKey().value()).collect(Collectors.toSet());
        if (!boundKeys.containsAll(selectedIterations)) throw invalid("所选迭代未挂载到该窗口");
        var selected = allBindings.stream().filter(b -> selectedIterations.isEmpty() || selectedIterations.contains(b.getIterationKey().value())).toList();
        if (selected.isEmpty()) throw invalid("窗口没有可预览的迭代");

        Map<String, CodeRepository> repoMap = new LinkedHashMap<>();
        for (String id : repos) {
            var repo = repositories.findById(RepoId.of(id)).orElseThrow(() -> invalid("仓库不存在"));
            if (!Objects.equals(window.getGroupCode(), repo.getGroupCode())) throw invalid("仓库与窗口不在同一分组");
            repoMap.put(id, repo);
        }
        Map<String, Iteration> iterationMap = new HashMap<>();
        Map<String, Map<String, IterationRepoVersionInfo>> versionMap = new HashMap<>();
        for (var binding : selected) {
            String ik = binding.getIterationKey().value();
            Iteration iteration = iterations.findByKey(binding.getIterationKey()).orElseThrow(() -> invalid("迭代不存在"));
            if (!Objects.equals(window.getGroupCode(), iteration.getGroupCode())) throw invalid("迭代与窗口不在同一分组");
            if (!selectedIterations.isEmpty() && iteration.getRepos().stream().noneMatch(id -> repoMap.containsKey(id.value())))
                throw invalid("所选迭代不包含任何所选仓库");
            iterationMap.put(ik, iteration);
            versionMap.put(ik, versions.listVersionInfo(ik).stream().collect(Collectors.toMap(IterationRepoVersionInfo::getRepoId, Function.identity())));
        }
        String fallbackRelease = ReleaseBranchNaming.resolve(window.getWindowKey(), settings.getNaming().orElse(null));
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Run run = Run.preview(operator, now);
        List<RunPlanSnapshot.Item> items = new ArrayList<>();
        for (var entry : repoMap.entrySet()) {
            int before = items.size();
            for (var binding : selected) {
                String ik = binding.getIterationKey().value();
                if (!iterationMap.get(ik).getRepos().contains(RepoId.of(entry.getKey()))) continue;
                var version = versionMap.get(ik).get(entry.getKey());
                if (version == null || version.getFeatureBranch() == null || version.getFeatureBranch().isBlank())
                    throw invalid("迭代仓库缺少已记录的 feature 分支");
                if (items.size() >= 1000) throw invalid("预览超过 1000 个执行项，请缩小选择范围");
                var item = RunItem.create(window.getWindowKey(), RepoId.of(entry.getKey()), binding.getIterationKey(), items.size() + 1, now);
                run.addItem(item);
                String release = binding.getReleaseBranch();
                var repo = entry.getValue();
                items.add(new RunPlanSnapshot.Item(item.getId().value(), item.getPlannedOrder(), repo.getId().value(), repo.getName(), ik,
                        repo.getGitProvider().name(), repo.getDefaultBranch(), version.getFeatureBranch(),
                        release == null || release.isBlank() ? fallbackRelease : release,
                        version.getBaseVersion(), version.getDevVersion(), version.getTargetVersion(), STEPS));
            }
            if (before == items.size()) throw invalid("所选仓库不属于所选窗口迭代");
        }
        var snapshot = new RunPlanSnapshot(window.getWindowKey(), window.getGroupCode(), window.getStatus().name(), window.isFrozen(), failFast, items);
        List<String> content = new ArrayList<>(Arrays.asList("LOCAL_PREVIEW_V1", snapshot.windowKey(), snapshot.groupCode(), snapshot.windowStatus(),
                Boolean.toString(snapshot.frozen()), Boolean.toString(failFast)));
        for (var item : items) {
            content.addAll(Arrays.asList(Integer.toString(item.plannedOrder()), item.repoId(), item.repoName(), item.iterationKey(), item.gitProvider(),
                    item.defaultBranch(), item.featureBranch(), item.releaseBranch(), item.baseVersion(), item.devVersion(), item.targetVersion()));
            content.addAll(item.steps());
        }
        var plan = new SavedRunPlan(run.getId().value(), windowId, operator, key, requestHash, PlanFingerprint.of(content), now, snapshot);
        runs.save(run);
        plans.save(plan);
        return plan;
    }

    @Transactional(readOnly = true)
    public SavedRunPlan get(String runId) { return plans.findByRunId(runId).orElseThrow(); }

    @Transactional(readOnly = true)
    public List<RunPlanSummary> list(String windowId) { return plans.listByWindow(windowId, 20); }

    private List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 1000 || values.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 255)) throw invalid("选择范围格式无效");
        return values.stream().map(String::trim).distinct().sorted().toList();
    }

    private static BusinessException invalid(String reason) { return BusinessException.of(ErrorCode.RUN_PLAN_INVALID_SCOPE, reason); }
}
