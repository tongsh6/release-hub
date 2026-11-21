package io.releasehub.application.run.plan;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.window.PlanItemView;
import io.releasehub.application.window.ReleaseBranchNaming;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.CloneUrl;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RunPlanAppService {
    private static final List<String> STEPS = List.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE");
    private static final List<String> CLEANUP_STEPS = List.of("MERGE_TO_MASTER", "CREATE_TAG", "TRIGGER_CI");
    private final RunPlanPort plans;
    private final RunPort runs;
    private final ReleaseWindowPort windows;
    private final WindowIterationPort bindings;
    private final IterationPort iterations;
    private final IterationRepoPort versions;
    private final CodeRepositoryPort repositories;
    private final SettingsPort settings;
    private final Clock clock;
    private final io.releasehub.application.version.VersionDeriverUseCase versionDeriver;

    @Transactional
    public SavedRunPlan create(String windowId, List<String> repoIds, List<String> iterationKeys,
                               boolean failFast, String operator, String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid("幂等键需为 8 至 128 位字母、数字或 ._:-");
        validateIdentity(windowId, operator);
        List<String> repos = normalize(repoIds);
        List<String> selectedIterations = normalize(iterationKeys);
        if (repos.isEmpty()) throw invalid("请至少选择一个仓库");
        String requestHash = requestHash(windowId, repos, selectedIterations, failFast);
        plans.lockRequest(operator, key);
        var existing = plans.findByRequest(operator, key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(requestHash)) throw BusinessException.of(ErrorCode.RUN_PLAN_KEY_CONFLICT);
            return existing.get();
        }
        Prepared prepared = prepare(windowId, repos, selectedIterations, failFast, operator, false, false);
        return save(prepared, windowId, key, requestHash);
    }

    /** 短事务仅保存已完整校验的执行计划；Git 写操作在事务返回后由执行器进行。 */
    @Transactional
    public Run admit(String windowId, List<String> repoIds, List<String> iterationKeys,
                     boolean failFast, String operator) {
        validateIdentity(windowId, operator);
        List<String> repos = normalize(repoIds);
        List<String> selectedIterations = normalize(iterationKeys);
        if (repos.isEmpty()) throw invalid("请至少选择一个仓库");
        Prepared prepared = prepare(lockWindow(windowId), repos, selectedIterations, failFast, operator, true, false);
        save(prepared, windowId, "execute:" + prepared.run().getId().value(),
                requestHash(windowId, repos, selectedIterations, failFast));
        return prepared.run();
    }

    /** 关闭受理返回已有或首次创建的仓库级 Run；执行器只消费首次受理。 */
    @Transactional
    public CleanupAdmission admitCleanup(String windowId, String operator, boolean triggerCi) {
        validateIdentity(windowId, operator);
        var window = lockWindow(windowId);
        var existing = plans.findCleanupByWindowId(windowId);
        if (existing.isPresent()) {
            return existingCleanup(existing.get(), windowId, window.getWindowKey(), triggerCi);
        }
        if (plans.hasUnresolvedExecutionByWindowId(windowId))
            throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
        if (window.getStatus() != ReleaseWindowStatus.PUBLISHED || window.isFrozen())
            throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);

        // 复用同一窗口快照的完整引用校验、命名与挂载顺序，再聚合为每仓库一项。
        Prepared source = prepare(window, List.of(), List.of(), true, operator, false, true);
        if (source.snapshot().items().isEmpty()) throw invalid("窗口没有可收尾的仓库");
        Map<String, List<RunPlanSnapshot.Item>> byRepository = new LinkedHashMap<>();
        Map<String, RunItem> sourceItems = new HashMap<>();
        source.run().getItems().forEach(item -> sourceItems.put(item.getId().value(), item));
        source.snapshot().items().forEach(item ->
                byRepository.computeIfAbsent(item.repoId(), ignored -> new ArrayList<>()).add(item));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Run run = Run.start(RunType.WINDOW_CLEANUP, operator, now);
        List<RunPlanSnapshot.Item> items = new ArrayList<>();
        Set<String> remoteMappings = new HashSet<>();
        for (var entry : byRepository.entrySet()) {
            var participants = entry.getValue();
            var planned = participants.get(0);
            var original = sourceItems.get(planned.itemId());
            String cloneUrl = original.getMetadata().get("plan.cloneUrl");
            if (!remoteMappings.add(CloneUrl.parse(cloneUrl).canonicalKey()))
                throw invalid("多个仓库指向同一远端，不能重复收尾");
            validateCleanupRef(planned.defaultBranch(), "默认分支");
            validateCleanupRef(planned.releaseBranch(), "release 分支");
            if (planned.defaultBranch().equals(planned.releaseBranch()))
                throw invalid("收尾 release 与默认目标分支不能相同");
            String targetVersion = cleanupTargetVersion(participants);
            String tagName = targetVersion == null ? null : "v" + targetVersion;
            if (tagName != null) validateCleanupRef(tagName, "Tag");
            if (triggerCi && tagName == null)
                throw invalid("触发 CI 需要所有参与迭代一致的明确版本和稳定 Tag");
            String ciRef = triggerCi ? tagName : null;
            List<String> keys = participants.stream().map(RunPlanSnapshot.Item::iterationKey).toList();
            RunItem item = RunItem.createForRepository(window.getWindowKey(), RepoId.of(entry.getKey()), items.size() + 1, now);
            for (String field : List.of("plan.windowId", "plan.releaseBranch", "plan.defaultBranch", "plan.cloneUrl", "plan.gitProvider"))
                item.putMetadata(field, original.getMetadata().get(field));
            item.putMetadata("plan.failFast", "true");
            item.putMetadata("plan.participatingIterationKeys", iterationKeysJson(keys));
            item.putMetadata("plan.tagName", tagName);
            item.putMetadata("plan.triggerCi", Boolean.toString(triggerCi));
            item.putMetadata("plan.ciRef", ciRef);
            run.addItem(item);
            items.add(new RunPlanSnapshot.Item(item.getId().value(), item.getPlannedOrder(), planned.repoId(), planned.repoName(),
                    null, planned.gitProvider(), planned.defaultBranch(), null, null, planned.releaseBranch(),
                    null, null, targetVersion, CLEANUP_STEPS, keys, tagName, triggerCi, ciRef));
        }
        var snapshot = new RunPlanSnapshot(window.getWindowKey(), window.getGroupCode(), window.getStatus().name(),
                window.isFrozen(), true, items);
        save(new Prepared(run, snapshot), windowId, "cleanup:" + run.getId().value(), cleanupRequestHash(windowId, triggerCi));
        window.close(now);
        windows.save(window);
        return new CleanupAdmission(run, true);
    }

    /** 向唯一调用方移交本次受理的可变聚合；服务不缓存 Run，HTTP 层只输出 RunView。 */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Run 是调用内新建或重建的聚合，服务不保留其引用；返回可变对象供执行器推进是显式契约")
    public record CleanupAdmission(Run run, boolean created) {}

    private CleanupAdmission existingCleanup(SavedRunPlan saved, String windowId, String windowKey, boolean triggerCi) {
        Run run = runs.findById(saved.runId()).orElseThrow(() -> BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE));
        RunPlanSnapshot snapshot = saved.snapshot();
        if (!Objects.equals(saved.windowId(), windowId) || !Objects.equals(snapshot.windowKey(), windowKey)
                || run.getRunType() != RunType.WINDOW_CLEANUP || run.getExecutionStatus() == RunExecutionStatus.PLANNED
                || !Objects.equals(run.getId().value(), saved.runId()) || !Objects.equals(run.getOperator(), saved.operator())
                || snapshot.items().isEmpty() || snapshot.items().size() != run.getItems().size()
                || !Objects.equals(saved.planHash(), planHash(run, snapshot)))
            throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
        if (!Objects.equals(saved.requestHash(), cleanupRequestHash(windowId, triggerCi)))
            throw BusinessException.of(ErrorCode.RUN_PLAN_KEY_CONFLICT);
        Map<String, RunItem> runItems = new HashMap<>();
        for (RunItem item : run.getItems()) {
            if (runItems.putIfAbsent(item.getId().value(), item) != null)
                throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
        }
        Set<String> repos = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (var planned : snapshot.items()) {
            RunItem item = runItems.get(planned.itemId());
            if (item == null || item.getIterationKey() != null || planned.iterationKey() != null
                    || !repos.add(planned.repoId()) || !ids.add(planned.itemId())
                    || !Objects.equals(item.getRepo().value(), planned.repoId())
                    || !Objects.equals(item.getWindowKey(), windowKey)
                    || item.getPlannedOrder() != planned.plannedOrder()
                    || planned.participatingIterationKeys().isEmpty()
                    || planned.participatingIterationKeys().stream().anyMatch(key -> key == null || key.isBlank())
                    || new HashSet<>(planned.participatingIterationKeys()).size() != planned.participatingIterationKeys().size()
                    || planned.triggerCi() != triggerCi
                    || !Objects.equals(planned.ciRef(), triggerCi ? planned.tagName() : null)
                    || (triggerCi && (planned.tagName() == null || planned.tagName().isBlank())))
                throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
            Map<String, String> metadata = item.getMetadata();
            if (!Objects.equals(metadata.get("plan.windowId"), windowId)
                    || !Objects.equals(metadata.get("plan.releaseBranch"), planned.releaseBranch())
                    || !Objects.equals(metadata.get("plan.defaultBranch"), planned.defaultBranch())
                    || !Objects.equals(metadata.get("plan.gitProvider"), planned.gitProvider())
                    || !GitProvider.GITLAB.name().equals(planned.gitProvider())
                    || metadata.get("plan.cloneUrl") == null
                    || !Objects.equals(metadata.get("plan.participatingIterationKeys"), iterationKeysJson(planned.participatingIterationKeys()))
                    || !Objects.equals(metadata.get("plan.tagName"), planned.tagName())
                    || !Objects.equals(metadata.get("plan.triggerCi"), Boolean.toString(planned.triggerCi()))
                    || !Objects.equals(metadata.get("plan.ciRef"), planned.ciRef()))
                throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
        }
        return new CleanupAdmission(run, false);
    }

    private String cleanupTargetVersion(List<RunPlanSnapshot.Item> participants) {
        Set<String> candidates = new HashSet<>();
        boolean missing = false;
        for (var item : participants) {
            String target = nonBlank(item.targetVersion());
            String dev = nonBlank(item.devVersion());
            String derived = dev == null ? null : nonBlank(versionDeriver.deriveTargetVersion(dev));
            if (target != null && derived != null && !target.equals(derived))
                throw invalid("迭代的目标版本与开发版本派生结果不一致: " + item.iterationKey());
            String resolved = target == null ? derived : target;
            if (resolved == null) missing = true;
            else candidates.add(resolved);
        }
        if (candidates.size() > 1) throw invalid("同一仓库的参与迭代存在不同发布目标版本");
        return missing || candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateCleanupRef(String ref, String label) {
        if (ref == null || ref.isBlank() || ref.equals("@") || ref.startsWith("/") || ref.startsWith("-")
                || ref.endsWith("/") || ref.endsWith(".") || ref.contains("..") || ref.contains("@{") || ref.contains("//")
                || ref.chars().anyMatch(ch -> ch <= 32 || ch == 127 || "~^:?*[\\".indexOf(ch) >= 0)
                || Arrays.stream(ref.split("/", -1)).anyMatch(part -> part.startsWith(".") || part.endsWith(".lock")))
            throw invalid(label + "格式无效");
    }

    private String cleanupRequestHash(String windowId, boolean triggerCi) {
        return PlanFingerprint.of(List.of("WINDOW_CLEANUP_V1", windowId, Boolean.toString(triggerCi)));
    }

    /** metadata 使用 JSON 数组；键来自既有领域输入，必须转义而不能用逗号拼接。 */
    private String iterationKeysJson(List<String> keys) {
        List<String> encoded = new ArrayList<>();
        for (String key : keys) {
            StringBuilder value = new StringBuilder("\"");
            for (int index = 0; index < key.length(); index++) {
                char ch = key.charAt(index);
                if (ch == '\\' || ch == '"') value.append('\\').append(ch);
                else if (ch < 32) value.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                else value.append(ch);
            }
            encoded.add(value.append('"').toString());
        }
        return "[" + String.join(",", encoded) + "]";
    }

    /** 重试复制源快照的精确 Item 集合，不从当前仓库和迭代重新计算交叉范围。 */
    @Transactional
    public Run admitRetry(Run previous, List<String> selectedItemIds, String operator) {
        if (previous == null || previous.getRunType() != RunType.WINDOW_ORCHESTRATION
                || previous.getExecutionStatus() == RunExecutionStatus.PLANNED
                || previous.getExecutionStatus() == RunExecutionStatus.RUNNING
                || previous.getExecutionStatus() == RunExecutionStatus.NEEDS_REVIEW
                || previous.getFinishedAt() == null)
            throw BusinessException.of(ErrorCode.RUN_PLAN_NOT_EXECUTABLE);
        SavedRunPlan source = plans.findByRunId(previous.getId().value())
                .orElseThrow(() -> invalid("源 Run 没有不可变执行计划，不能重试"));
        validateIdentity(source.windowId(), operator);
        List<String> selectedIds = normalize(selectedItemIds);
        if (selectedIds.isEmpty()) throw invalid("请至少选择一个待重试 Item");
        RunPlanSnapshot snapshot = source.snapshot();
        var selected = snapshot.items().stream().filter(item -> selectedIds.contains(item.itemId()))
                .sorted(Comparator.comparingInt(RunPlanSnapshot.Item::plannedOrder)).toList();
        if (selected.size() != selectedIds.size()) throw invalid("所选 Item 不属于源 Run 计划");

        var window = lockWindow(source.windowId());
        if (window.getStatus() == ReleaseWindowStatus.CLOSED || window.isFrozen()) throw invalid("已关闭或冻结窗口不能重试");
        if (!Objects.equals(window.getWindowKey(), snapshot.windowKey())
                || !Objects.equals(window.getGroupCode(), snapshot.groupCode())) throw invalid("窗口范围已变更，不能重解释原计划");
        Set<String> bound = new HashSet<>();
        bindings.listByWindow(window.getId()).forEach(binding -> bound.add(binding.getIterationKey().value()));
        Map<String, RunItem> sourceItems = new HashMap<>();
        for (var item : previous.getItems()) {
            if (sourceItems.putIfAbsent(item.getId().value(), item) != null) throw invalid("源 Run Item 标识重复");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, now);
        List<RunPlanSnapshot.Item> retryItems = new ArrayList<>();
        for (var planned : selected) {
            RunItem original = sourceItems.get(planned.itemId());
            if (original == null || !isRetryable(original.getFinalResult())) throw invalid("只能重试失败、阻塞或因阻塞未执行的 Item");
            if (snapshot.failFast() && original.getFinalResult() == RunItemResult.SKIPPED_DUE_TO_BLOCK
                    && sourceItems.values().stream().anyMatch(earlier -> earlier.getRepo().equals(original.getRepo())
                    && earlier.getPlannedOrder() < original.getPlannedOrder()
                    && (earlier.getFinalResult() == RunItemResult.FAILED || earlier.getFinalResult() == RunItemResult.MERGE_BLOCKED)
                    && !selectedIds.contains(earlier.getId().value())))
                throw invalid("重试跳过项必须显式选择该仓库更早的失败或阻塞项");
            if (!Objects.equals(original.getWindowKey(), snapshot.windowKey())
                    || !Objects.equals(original.getRepo().value(), planned.repoId())
                    || !Objects.equals(original.getIterationKey().value(), planned.iterationKey())
                    || original.getPlannedOrder() != planned.plannedOrder()) throw invalid("源 Item 与不可变计划不一致");
            if (!bound.contains(planned.iterationKey())) throw invalid("所选迭代已解除挂载");
            var iteration = iterations.findByKey(original.getIterationKey()).orElseThrow(() -> invalid("所选迭代已不存在"));
            if (!iteration.getRepos().contains(original.getRepo())
                    || !Objects.equals(iteration.getGroupCode(), snapshot.groupCode())) throw invalid("所选迭代仓库绑定已变更");
            var repo = repositories.findById(original.getRepo()).orElseThrow(() -> invalid("所选仓库已不存在"));
            if (repo.getGitProvider() != GitProvider.GITLAB || !"GITLAB".equals(planned.gitProvider()))
                throw invalid("一期编排仅支持 GitLab 仓库");
            Map<String, String> metadata = original.getMetadata();
            String featureMode = planned.featureBranchMode();
            if (featureMode == null || Arrays.stream(BranchCreationMode.values()).noneMatch(mode -> mode.name().equals(featureMode)))
                throw invalid("源计划缺少有效的 feature 创建模式");
            if (!Objects.equals(repo.getGroupCode(), snapshot.groupCode())
                    || !Objects.equals(snapshotCloneUrl(repo), metadata.get("plan.cloneUrl"))
                    || !Objects.equals(repo.getGitProvider().name(), planned.gitProvider())) throw invalid("仓库映射已变更，不能重解释原计划");
            if (!Objects.equals(metadata.get("plan.windowId"), source.windowId())
                    || !Objects.equals(metadata.get("plan.featureBranch"), planned.featureBranch())
                    || !Objects.equals(metadata.get("plan.featureBranchMode"), featureMode)
                    || !Objects.equals(metadata.get("plan.releaseBranch"), planned.releaseBranch())
                    || !Objects.equals(metadata.get("plan.defaultBranch"), planned.defaultBranch())
                    || !Objects.equals(metadata.get("plan.gitProvider"), planned.gitProvider())
                    || !Objects.equals(metadata.get("plan.failFast"), Boolean.toString(snapshot.failFast())))
                throw invalid("源 Item 缺少一致的计划执行参数");

            RunItem item = RunItem.create(snapshot.windowKey(), original.getRepo(), original.getIterationKey(), planned.plannedOrder(), now);
            for (String field : List.of("plan.windowId", "plan.featureBranch", "plan.releaseBranch", "plan.defaultBranch",
                    "plan.failFast", "plan.cloneUrl", "plan.gitProvider", "plan.featureBranchMode")) item.putMetadata(field, metadata.get(field));
            item.putMetadata("retry.sourceRunId", previous.getId().value());
            item.putMetadata("retry.sourceItemId", original.getId().value());
            run.addItem(item);
            retryItems.add(new RunPlanSnapshot.Item(item.getId().value(), item.getPlannedOrder(), planned.repoId(), planned.repoName(),
                    planned.iterationKey(), planned.gitProvider(), planned.defaultBranch(), planned.featureBranch(), planned.featureBranchMode(), planned.releaseBranch(),
                    planned.baseVersion(), planned.devVersion(), planned.targetVersion(), planned.steps(),
                    planned.participatingIterationKeys(), planned.tagName(), planned.triggerCi(), planned.ciRef()));
        }
        var retrySnapshot = new RunPlanSnapshot(snapshot.windowKey(), snapshot.groupCode(), window.getStatus().name(),
                window.isFrozen(), snapshot.failFast(), retryItems);
        List<String> request = new ArrayList<>(List.of("RUN_RETRY_V1", previous.getId().value()));
        selected.forEach(item -> request.add(item.itemId()));
        save(new Prepared(run, retrySnapshot), source.windowId(), "retry:" + run.getId().value(), PlanFingerprint.of(request));
        return run;
    }

    private boolean isRetryable(RunItemResult result) {
        return result == RunItemResult.FAILED || result == RunItemResult.MERGE_BLOCKED || result == RunItemResult.SKIPPED_DUE_TO_BLOCK;
    }

    /** 窗口页和并行窗口比较共用执行计划的范围、目标分支与稳定顺序，不保存 Run。 */
    @Transactional(readOnly = true)
    public List<PlanItemView> previewWindow(String windowId) {
        RunPlanSnapshot snapshot = previewSnapshot(windowId);
        return snapshot.items().stream().map(item -> new PlanItemView(
                snapshot.windowKey(), item.repoId(), item.iterationKey(), item.plannedOrder(), null)).toList();
    }

    /** 只读窗口快照供计划与分支事实展示共用；关闭、冻结和空范围仍可查看。 */
    @Transactional(readOnly = true)
    public RunPlanSnapshot previewSnapshot(String windowId) {
        validateIdentity(windowId, "preview");
        return prepare(windowId, List.of(), List.of(), true, "preview", false, true).snapshot();
    }

    private ReleaseWindow lockWindow(String windowId) {
        return windows.findByIdForUpdate(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
    }

    private Prepared prepare(String windowId, List<String> selectedRepos, List<String> selectedIterations,
                             boolean failFast, String operator, boolean execute, boolean windowView) {
        var window = windows.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
        return prepare(window, selectedRepos, selectedIterations, failFast, operator, execute, windowView);
    }

    private Prepared prepare(ReleaseWindow window, List<String> selectedRepos, List<String> selectedIterations,
                             boolean failFast, String operator, boolean execute, boolean windowView) {
        String windowId = window.getId().value();
        if (window.getGroupCode() == null || window.getGroupCode().isBlank()) throw invalid("窗口缺少分组信息");
        if (!windowView && window.getStatus() == ReleaseWindowStatus.CLOSED) throw invalid("已关闭窗口不能创建新计划");
        if (execute && window.isFrozen()) throw invalid("已冻结窗口不能执行编排");
        var allBindings = bindings.listByWindow(window.getId()).stream()
                .sorted(Comparator.comparing(WindowIteration::getAttachAt)
                        .thenComparing(b -> b.getIterationKey().value())).toList();
        Set<String> boundKeys = new HashSet<>();
        Map<String, Iteration> iterationMap = new LinkedHashMap<>();
        for (var binding : allBindings) {
            String key = binding.getIterationKey().value();
            if (!boundKeys.add(key)) throw invalid("窗口包含重复迭代绑定");
            Iteration iteration = iterations.findByKey(binding.getIterationKey()).orElseThrow(() -> invalid("绑定迭代不存在: " + key));
            if (!Objects.equals(window.getGroupCode(), iteration.getGroupCode())) throw invalid("迭代与窗口不在同一分组");
            iterationMap.put(key, iteration);
        }
        if (!boundKeys.containsAll(selectedIterations)) throw invalid("所选迭代未挂载到该窗口");
        var selected = allBindings.stream().filter(b -> selectedIterations.isEmpty()
                || selectedIterations.contains(b.getIterationKey().value())).toList();
        if (!windowView && selected.isEmpty()) throw invalid("窗口没有可编排的迭代");
        List<String> repos = windowView ? iterationMap.values().stream().flatMap(i -> i.getRepos().stream())
                .map(RepoId::value).distinct().sorted().toList() : selectedRepos;
        Map<String, CodeRepository> repoMap = new LinkedHashMap<>();
        for (String id : repos) {
            var repo = repositories.findById(RepoId.of(id)).orElseThrow(() -> invalid("仓库不存在: " + id));
            if (!Objects.equals(window.getGroupCode(), repo.getGroupCode())) throw invalid("仓库与窗口不在同一分组");
            if (repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()) throw invalid("仓库缺少默认分支: " + id);
            if (repo.getGitProvider() != GitProvider.GITLAB) throw invalid("一期编排仅支持 GitLab 仓库");
            snapshotCloneUrl(repo);
            repoMap.put(id, repo);
        }
        Map<String, Map<String, IterationRepoVersionInfo>> versionMap = new HashMap<>();
        for (var binding : selected) {
            String key = binding.getIterationKey().value();
            Iteration iteration = iterationMap.get(key);
            if (!selectedIterations.isEmpty() && iteration.getRepos().stream().noneMatch(id -> repoMap.containsKey(id.value())))
                throw invalid("所选迭代不包含任何所选仓库");
            Map<String, IterationRepoVersionInfo> byRepo = new HashMap<>();
            for (var version : versions.listVersionInfo(key)) {
                if (byRepo.putIfAbsent(version.getRepoId(), version) != null) throw invalid("迭代仓库配置重复: " + key);
            }
            versionMap.put(key, byRepo);
        }
        String fallbackRelease = ReleaseBranchNaming.resolve(window.getWindowKey(), settings.getNaming().orElse(null));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Run run = execute ? Run.start(RunType.WINDOW_ORCHESTRATION, operator, now) : Run.preview(operator, now);
        List<RunPlanSnapshot.Item> items = new ArrayList<>();
        for (var entry : repoMap.entrySet()) {
            RepoId repoId = RepoId.of(entry.getKey());
            CodeRepository repo = entry.getValue();
            List<String> recordedTargets = allBindings.stream()
                    .filter(binding -> iterationMap.get(binding.getIterationKey().value()).getRepos().contains(repoId))
                    .map(WindowIteration::getReleaseBranch).filter(value -> value != null && !value.isBlank()).distinct().toList();
            if (recordedTargets.size() > 1) throw invalid("同一窗口仓库存在多个 release 目标: " + entry.getKey());
            String release = recordedTargets.isEmpty() ? fallbackRelease : recordedTargets.get(0);
            int before = items.size();
            for (var binding : selected) {
                String key = binding.getIterationKey().value();
                if (!iterationMap.get(key).getRepos().contains(repoId)) continue;
                var version = versionMap.get(key).get(entry.getKey());
                String feature = featureBranch(key, version);
                BranchCreationMode featureMode = version == null || version.getBranchCreationMode() == null
                        ? BranchCreationMode.AUTO : version.getBranchCreationMode();
                if (items.size() >= 1000) throw invalid("计划超过 1000 个执行项，请缩小选择范围");
                var item = RunItem.create(window.getWindowKey(), repoId, binding.getIterationKey(), items.size() + 1, now);
                item.putMetadata("plan.windowId", windowId);
                item.putMetadata("plan.featureBranch", feature);
                item.putMetadata("plan.featureBranchMode", featureMode.name());
                item.putMetadata("plan.releaseBranch", release);
                item.putMetadata("plan.defaultBranch", repo.getDefaultBranch());
                item.putMetadata("plan.failFast", Boolean.toString(failFast));
                item.putMetadata("plan.cloneUrl", repo.getCloneUrl());
                item.putMetadata("plan.gitProvider", repo.getGitProvider().name());
                run.addItem(item);
                items.add(new RunPlanSnapshot.Item(item.getId().value(), item.getPlannedOrder(), repoId.value(), repo.getName(), key,
                        repo.getGitProvider().name(), repo.getDefaultBranch(), feature, featureMode.name(), release,
                        version == null ? null : version.getBaseVersion(), version == null ? null : version.getDevVersion(),
                        version == null ? null : version.getTargetVersion(), STEPS, List.of(key), null, false, null));
            }
            if (before == items.size()) throw invalid("所选仓库不属于所选窗口迭代");
        }
        return new Prepared(run, new RunPlanSnapshot(window.getWindowKey(), window.getGroupCode(),
                window.getStatus().name(), window.isFrozen(), failFast, items));
    }

    private String snapshotCloneUrl(CodeRepository repo) {
        String value = repo.getCloneUrl();
        CloneUrl.parse(value);
        if (value.startsWith("git@")) return value;
        try {
            URI uri = URI.create(value);
            boolean standardSshUser = "ssh".equalsIgnoreCase(uri.getScheme()) && "git".equals(uri.getRawUserInfo());
            if ((uri.getRawUserInfo() != null && !standardSshUser) || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw invalid("仓库地址不能包含凭据、查询或片段，请使用独立访问令牌配置");
            return value;
        } catch (IllegalArgumentException error) {
            throw invalid("仓库地址格式无效");
        }
    }

    private String featureBranch(String iterationKey, IterationRepoVersionInfo version) {
        if (version != null && version.getFeatureBranch() != null && !version.getFeatureBranch().isBlank())
            return version.getFeatureBranch();
        if (version != null && version.getBranchCreationMode() != null && version.getBranchCreationMode() != BranchCreationMode.AUTO)
            throw invalid("自定义迭代分支缺少名称: " + iterationKey);
        // 与 IterationAppService 的 AUTO 规则一致；记录的 NAMED/EXISTING 分支优先。
        return "feature/" + iterationKey;
    }

    private SavedRunPlan save(Prepared prepared, String windowId, String key, String requestHash) {
        Run run = prepared.run();
        RunPlanSnapshot snapshot = prepared.snapshot();
        var saved = new SavedRunPlan(run.getId().value(), windowId, run.getOperator(), key, requestHash,
                planHash(run, snapshot), run.getStartedAt(), snapshot);
        runs.save(run);
        plans.save(saved);
        return saved;
    }

    private String planHash(Run run, RunPlanSnapshot snapshot) {
        List<String> content = new ArrayList<>(Arrays.asList("RUN_PLAN_V2", run.getRunType().name(), snapshot.windowKey(), snapshot.groupCode(),
                snapshot.windowStatus(), Boolean.toString(snapshot.frozen()), Boolean.toString(snapshot.failFast())));
        for (var item : snapshot.items()) {
            content.addAll(Arrays.asList(Integer.toString(item.plannedOrder()), item.repoId(), item.repoName(), item.iterationKey(),
                    item.gitProvider(), item.defaultBranch(), item.featureBranch(), item.featureBranchMode(), item.releaseBranch(),
                    item.baseVersion(), item.devVersion(), item.targetVersion(), item.tagName(), Boolean.toString(item.triggerCi()), item.ciRef(),
                    Integer.toString(item.participatingIterationKeys().size())));
            content.addAll(item.participatingIterationKeys());
            content.add(Integer.toString(item.steps().size()));
            content.addAll(item.steps());
        }
        // 地址也参与指纹，但公开快照不暴露凭据或连接地址。
        Map<String, String> cloneUrls = new HashMap<>();
        run.getItems().forEach(item -> cloneUrls.put(item.getId().value(), item.getMetadata().get("plan.cloneUrl")));
        snapshot.items().forEach(item -> content.add(cloneUrls.get(item.itemId())));
        return PlanFingerprint.of(content);
    }

    @Transactional(readOnly = true)
    public SavedRunPlan get(String runId) { return plans.findByRunId(runId).orElseThrow(); }

    @Transactional(readOnly = true)
    public List<RunPlanSummary> list(String windowId) { return plans.listByWindow(windowId, 20); }

    private String requestHash(String windowId, List<String> repos, List<String> selectedIterations, boolean failFast) {
        List<String> input = new ArrayList<>(List.of("LOCAL_PREVIEW_V1", windowId, Boolean.toString(failFast), Integer.toString(repos.size())));
        input.addAll(repos);
        input.add(Integer.toString(selectedIterations.size()));
        input.addAll(selectedIterations);
        return PlanFingerprint.of(input);
    }

    private void validateIdentity(String windowId, String operator) {
        if (windowId == null || windowId.isBlank()) throw invalid("缺少窗口标识");
        if (operator == null || operator.isBlank()) throw invalid("缺少认证主体");
    }

    private List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 1000 || values.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 255))
            throw invalid("选择范围格式无效");
        return values.stream().map(String::trim).distinct().sorted().toList();
    }

    private record Prepared(Run run, RunPlanSnapshot snapshot) {}

    private static BusinessException invalid(String reason) { return BusinessException.of(ErrorCode.RUN_PLAN_INVALID_SCOPE, reason); }
}