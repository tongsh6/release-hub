package io.releasehub.application.releasewindow;

import io.releasehub.application.group.GroupPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.run.RunAppService;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.PlanItemView;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseWindowAppService {

    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final RunAppService runAppService;
    private final GroupPort groupPort;
    private final IterationPort iterationPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ReleaseWindowView create(String name, String description, Instant plannedReleaseAt, String groupCode) {
        String windowKey = generateWindowKey();
        ensureLeafGroup(groupCode);
        ReleaseWindow rw = ReleaseWindow.createDraft(windowKey, name, description, plannedReleaseAt, groupCode, Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    /**
     * 自动生成发布窗口标识
     * 格式: RW-yyyyMMdd-xxxx (xxxx 为随机4位)
     */
    private String generateWindowKey() {
        String datePart = KEY_DATE_FORMAT.format(Instant.now(clock).atZone(ZoneId.systemDefault()));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RW-" + datePart + "-" + randomPart;
    }

    private void ensureLeafGroup(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw ValidationException.groupCodeRequired();
        }
        groupPort.findByCode(groupCode)
                 .orElseThrow(() -> NotFoundException.groupCode(groupCode));
        if (groupPort.countChildren(groupCode) > 0) {
            throw BusinessException.groupNotLeaf(groupCode);
        }
    }

    public ReleaseWindowView get(String id) {
        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(id))
                                            .orElseThrow(() -> NotFoundException.releaseWindow(id));
        return enrichParallelContext(ReleaseWindowView.from(rw));
    }

    @Transactional
    public void delete(String id) {
        ReleaseWindowId windowId = ReleaseWindowId.of(id);
        ReleaseWindow rw = releaseWindowPort.findById(windowId)
                                            .orElseThrow(() -> NotFoundException.releaseWindow(id));
        if (rw.getStatus() != ReleaseWindowStatus.DRAFT) {
            throw BusinessException.rwDeleteBlocked(id);
        }
        if (!windowIterationPort.listByWindow(windowId).isEmpty()) {
            throw BusinessException.rwDeleteBlocked(id);
        }
        releaseWindowPort.deleteById(windowId);
    }

    public List<ReleaseWindowView> list() {
        List<ReleaseWindowView> views = releaseWindowPort.findAll().stream()
                                .map(ReleaseWindowView::from)
                                .toList();
        return enrichParallelContext(views);
    }

    public PageResult<ReleaseWindowView> listPaged(String name, ReleaseWindowStatus status, int page, int size) {
        PageResult<ReleaseWindow> result = releaseWindowPort.findPaged(name, status, page, size);
        List<ReleaseWindowView> views = result.items().stream()
                                              .map(ReleaseWindowView::from)
                                              .toList();
        return new PageResult<>(enrichParallelContext(views), result.total());
    }

    public PageResult<ReleaseWindowView> listPaged(String name, ReleaseWindowStatus status, String groupCode, int page, int size) {
        if (groupCode == null || groupCode.isBlank()) {
            return listPaged(name, status, page, size);
        }
        groupPort.findByCode(groupCode)
                 .orElseThrow(() -> NotFoundException.groupCode(groupCode));
        List<String> groupCodes = collectGroupCodes(groupCode);
        PageResult<ReleaseWindow> result = releaseWindowPort.findPaged(name, status, groupCodes, page, size);
        List<ReleaseWindowView> views = result.items().stream()
                                              .map(ReleaseWindowView::from)
                                              .toList();
        return new PageResult<>(enrichParallelContext(views), result.total());
    }

    public ReleaseWindowParallelScopeView getParallelScope(String id) {
        ReleaseWindow current = findById(id);
        List<ReleaseWindow> sameGroupActiveWindows = activeWindowsInSameGroup(current.getGroupCode());
        List<ReleaseWindowParallelScopeView.ParallelWindowView> windows = sameGroupActiveWindows.stream()
                .map(this::toParallelWindowView)
                .toList();
        return new ReleaseWindowParallelScopeView(
                current.getId().value(),
                current.getWindowKey(),
                current.getGroupCode(),
                windows.size(),
                windows
        );
    }

    private ReleaseWindowParallelScopeView.ParallelWindowView toParallelWindowView(ReleaseWindow window) {
        List<WindowIteration> bindings = windowIterationPort.listByWindow(window.getId());
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));
        Map<IterationKey, Integer> order = computePlannedOrder(bindings);

        Set<String> repoIds = new LinkedHashSet<>();
        List<PlanItemView> planItems = new ArrayList<>();
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null) {
                continue;
            }
            for (RepoId repoId : iteration.getRepos()) {
                repoIds.add(repoId.value());
                planItems.add(new PlanItemView(
                        window.getWindowKey(),
                        repoId.value(),
                        wi.getIterationKey().value(),
                        order.getOrDefault(wi.getIterationKey(), 0),
                        null
                ));
            }
        }

        return new ReleaseWindowParallelScopeView.ParallelWindowView(
                window.getId().value(),
                window.getWindowKey(),
                window.getName(),
                window.getStatus().name(),
                window.getPlannedReleaseAt(),
                bindings.size(),
                repoIds.size(),
                planItems
        );
    }

    private Map<IterationKey, Integer> computePlannedOrder(List<WindowIteration> bindings) {
        List<IterationKey> orderedKeys = bindings.stream()
                .sorted(Comparator.comparing(WindowIteration::getAttachAt))
                .map(WindowIteration::getIterationKey)
                .distinct()
                .toList();
        return orderedKeys.stream().collect(Collectors.toMap(key -> key, key -> orderedKeys.indexOf(key) + 1));
    }

    private ReleaseWindowView enrichParallelContext(ReleaseWindowView view) {
        return applyParallelContext(view, collectActiveWindowKeysByGroup());
    }

    private ReleaseWindowView applyParallelContext(ReleaseWindowView view, Map<String, List<String>> activeKeysByGroup) {
        if (view == null || view.getGroupCode() == null || view.getGroupCode().isBlank()) {
            return view;
        }
        List<String> activeKeys = activeKeysByGroup.getOrDefault(view.getGroupCode(), List.of());
        view.setParallelActiveWindowCount(activeKeys.size());
        view.setParallelActiveWindowKeys(activeKeys);
        return view;
    }

    private List<ReleaseWindowView> enrichParallelContext(List<ReleaseWindowView> views) {
        Map<String, List<String>> activeKeysByGroup = collectActiveWindowKeysByGroup();
        return views.stream()
                .map(view -> applyParallelContext(view, activeKeysByGroup))
                .toList();
    }

    private Map<String, List<String>> collectActiveWindowKeysByGroup() {
        return releaseWindowPort.findAll().stream()
                .filter(this::isActiveParallelWindow)
                .filter(window -> window.getGroupCode() != null && !window.getGroupCode().isBlank())
                .sorted(Comparator.comparing(ReleaseWindow::getPlannedReleaseAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReleaseWindow::getCreatedAt))
                .collect(Collectors.groupingBy(
                        ReleaseWindow::getGroupCode,
                        Collectors.mapping(ReleaseWindow::getWindowKey, Collectors.toList())
                ));
    }

    private List<ReleaseWindow> activeWindowsInSameGroup(String groupCode) {
        return releaseWindowPort.findAll().stream()
                .filter(window -> groupCode.equals(window.getGroupCode()))
                .filter(this::isActiveParallelWindow)
                .sorted(Comparator.comparing(ReleaseWindow::getPlannedReleaseAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReleaseWindow::getCreatedAt))
                .toList();
    }

    private boolean isActiveParallelWindow(ReleaseWindow window) {
        return window.getStatus() == ReleaseWindowStatus.DRAFT || window.getStatus() == ReleaseWindowStatus.PUBLISHED;
    }

    private List<String> collectGroupCodes(String rootCode) {
        List<String> codes = new ArrayList<>();
        collectGroupCodes(rootCode, codes);
        return codes;
    }

    private void collectGroupCodes(String code, List<String> result) {
        result.add(code);
        for (var child : groupPort.findByParentCode(code)) {
            collectGroupCodes(child.getCode(), result);
        }
    }

    @Transactional
    public ReleaseWindowView publish(String id) {
        ReleaseWindow rw = findById(id);

        // 验证关联迭代
        List<WindowIteration> iterations = windowIterationPort.listByWindow(ReleaseWindowId.of(id));
        if (iterations.isEmpty()) {
            throw BusinessException.rwNoIterations(id);
        }

        rw.publish(Instant.now(clock));
        releaseWindowPort.save(rw);
        log.info("Published release window {}", id);

        // 事务提交后由 WindowLifecycleListener 触发自动编排
        eventPublisher.publishEvent(new WindowPublishedEvent(id));

        return ReleaseWindowView.from(rw);
    }

    private ReleaseWindow findById(String id) {
        return releaseWindowPort.findById(ReleaseWindowId.of(id))
                                .orElseThrow(() -> NotFoundException.releaseWindow(id));
    }

    @Transactional
    public ReleaseWindowView freeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.freeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView unfreeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.unfreeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView close(String id, String operator) {
        ReleaseWindow rw = findById(id);
        rw.close(Instant.now(clock));
        releaseWindowPort.save(rw);

        try {
            Run run = runAppService.executeCleanup(id, operator);
            log.info("Cleanup run {} completed for closing window {}", run.getId().value(), id);
        } catch (Exception e) {
            log.error("Failed to execute cleanup for closing window {}: {}", id, e.getMessage());
            // 关闭成功但收尾任务失败时，不回滚关闭状态
        }

        return ReleaseWindowView.from(rw);
    }

    /**
     * 获取发布窗口内各仓库的分支状态
     */
    public BranchStatusView getBranchStatus(String id) {
        ReleaseWindow rw = findById(id);
        List<WindowIteration> windowIterations = windowIterationPort.listByWindow(ReleaseWindowId.of(id));
        List<BranchStatusView.RepoBranchStatus> repoStatuses = new ArrayList<>();

        for (WindowIteration wi : windowIterations) {
            String iterationKey = wi.getIterationKey().value();
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null) {
                continue;
            }

            String releaseBranchName = windowIterationPort.getReleaseBranch(id, iterationKey);
            if (releaseBranchName == null) {
                releaseBranchName = "release/" + rw.getWindowKey();
            }
            String featureBranchName = "feature/" + iterationKey;

            for (RepoId repoId : iteration.getRepos()) {
                CodeRepository repo = codeRepositoryPort.findById(repoId).orElse(null);
                if (repo == null) {
                    continue;
                }

                GitBranchPort adapter = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                String repoUrl = repo.getCloneUrl();
                String token = repo.getGitAccessToken();

                // 查询 feature 分支状态
                GitBranchPort.BranchStatus featureStatus;
                try {
                    featureStatus = adapter.getBranchStatus(repoUrl, token, featureBranchName);
                } catch (Exception e) {
                    log.warn("Failed to get feature branch status for repo {}: {}", repo.getName(), e.getMessage());
                    featureStatus = GitBranchPort.BranchStatus.missing();
                }

                // 查询 release 分支状态
                GitBranchPort.BranchStatus releaseStatus;
                try {
                    releaseStatus = adapter.getBranchStatus(repoUrl, token, releaseBranchName);
                } catch (Exception e) {
                    log.warn("Failed to get release branch status for repo {}: {}", repo.getName(), e.getMessage());
                    releaseStatus = GitBranchPort.BranchStatus.missing();
                }

                String mergeStatus = releaseStatus.exists() ? "MERGED" : "PENDING";

                BranchStatusView.FeatureBranchInfo featureInfo = new BranchStatusView.FeatureBranchInfo(
                        featureBranchName, featureStatus.exists(), featureStatus.latestCommit());
                BranchStatusView.ReleaseBranchInfo releaseInfo = new BranchStatusView.ReleaseBranchInfo(
                        releaseBranchName, releaseStatus.exists(), releaseStatus.latestCommit(), mergeStatus);

                repoStatuses.add(new BranchStatusView.RepoBranchStatus(
                        repoId.value(), repo.getName(), repo.getCloneUrl(),
                        iterationKey, featureInfo, releaseInfo));
            }
        }

        return new BranchStatusView(id, rw.getWindowKey(), repoStatuses);
    }
}
