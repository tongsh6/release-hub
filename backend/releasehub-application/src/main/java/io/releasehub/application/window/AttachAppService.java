package io.releasehub.application.window;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.MergeStatus;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final IterationPort iterationPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationRepoPort iterationRepoPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final CodeRepositoryPort codeRepositoryPort;
    private final BranchRuleUseCase branchRuleUseCase;
    private final RunPort runPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public List<AttachResult> attach(String windowId, List<String> iterationKeys) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        if (releaseWindow.isFrozen()) {
            throw BusinessException.rwAlreadyFrozen();
        }
        Instant now = Instant.now(clock);
        Run run = Run.start(RunType.ATTACH_ITERATION, "system", now);
        int[] order = {0};

        List<AttachResult> results = iterationKeys.stream()
                .map(IterationKey::of)
                .map(iterationKey -> {
                    Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();

                    WindowIteration wi = windowIterationPort.attach(ReleaseWindowId.of(windowId), iterationKey, now);

                    List<AttachResult.RepoError> errors = new ArrayList<>();
                    for (RepoId repoId : iteration.getRepos()) {
                        RunItem item = RunItem.create(releaseWindow.getName(), repoId, iterationKey, ++order[0], now);
                        try {
                            setupReleaseBranchForRepo(releaseWindow, iteration, iterationKey, repoId, now, item);
                            run.addItem(item);
                        } catch (Exception e) {
                            String repoName = codeRepositoryPort.findById(repoId)
                                    .map(CodeRepository::getName)
                                    .orElse(repoId.value());
                            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            log.error("Failed to setup release branch for repo {} ({}) in window {}: {}",
                                    repoName, repoId.value(), windowId, message);
                            item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.FAILED, now, now, message));
                            item.setExecutedOrder(order[0]);
                            item.finishWith(RunItemResult.FAILED, now);
                            run.addItem(item);
                            errors.add(AttachResult.RepoError.of(repoId.value(), repoName, message));
                        }
                    }

                    return errors.isEmpty()
                            ? AttachResult.success(wi)
                            : AttachResult.partial(wi, errors);
                })
                .toList();

        run.finish(Instant.now(clock));
        runPort.save(run);
        log.info("[Attach] Run {} tracked: {} items", run.getId().value(), run.getItems().size());
        return results;
    }

    private void setupReleaseBranchForRepo(ReleaseWindow releaseWindow, Iteration iteration,
            IterationKey iterationKey, RepoId repoId, Instant now, RunItem item) {
        CodeRepository repo = codeRepositoryPort.findById(repoId)
                .orElseThrow(() -> NotFoundException.repository(repoId.value()));
        GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        String gitToken = repo.getGitAccessToken();
        String repoUrl = repo.getCloneUrl();

        String releaseBranch = "release/" + releaseWindow.getWindowKey();
        if (!branchRuleUseCase.isCompliant(releaseBranch)) {
            throw ValidationException.invalidParameter("branchName");
        }

        String featureBranch = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                .map(info -> info.getFeatureBranch())
                .orElse("feature/" + iterationKey.value());

        Instant s1 = Instant.now(clock);
        boolean branchCreated = gitBranchPort.createBranch(repoUrl, gitToken, releaseBranch, repo.getDefaultBranch());
        RunItemResult branchResult = branchCreated ? RunItemResult.BRANCH_CREATED : RunItemResult.BRANCH_EXISTS;
        item.addStep(new RunStep(ActionType.ENSURE_RELEASE, branchResult, s1, s1,
                branchCreated ? "Created release branch: " + releaseBranch : "Release branch exists: " + releaseBranch));

        Instant s2 = Instant.now(clock);
        GitBranchPort.MergeResult mergeResult = gitBranchPort.mergeBranch(
                repoUrl, gitToken, featureBranch, releaseBranch,
                "Merge " + featureBranch + " to " + releaseBranch + " for iteration " + iteration.getName());

        RunItemResult mergeOutcome;
        String mergeMessage;
        switch (mergeResult.status()) {
            case SUCCESS -> {
                mergeOutcome = RunItemResult.MERGED;
                mergeMessage = "Merged " + featureBranch + " → " + releaseBranch;
                windowIterationPort.updateLastMergeAt(
                        releaseWindow.getId().value(), iterationKey.value(), Instant.now(clock));
            }
            case CONFLICT -> {
                mergeOutcome = RunItemResult.MERGE_BLOCKED;
                mergeMessage = "Merge conflict: " + mergeResult.detail();
            }
            default -> {
                mergeOutcome = RunItemResult.FAILED;
                mergeMessage = "Merge failed: " + mergeResult.detail();
            }
        }
        item.addStep(new RunStep(ActionType.TRY_MERGE, mergeOutcome, s2, s2, mergeMessage));
        item.setExecutedOrder(item.getPlannedOrder());
        item.finishWith(mergeOutcome, Instant.now(clock));

        windowIterationPort.updateReleaseBranch(
                releaseWindow.getId().value(), iterationKey.value(), releaseBranch, now);
    }

    @Transactional
    public void detach(String windowId, String iterationKey) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        if (releaseWindow.isFrozen()) {
            throw BusinessException.rwAlreadyFrozen();
        }
        Iteration iteration = iterationPort.findByKey(IterationKey.of(iterationKey)).orElseThrow();
        String releaseBranch = "release/" + releaseWindow.getWindowKey();
        for (RepoId repoId : iteration.getRepos()) {
            codeRepositoryPort.findById(repoId).ifPresent(repo -> {
                GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                boolean archived = gitBranchPort.archiveBranch(
                        repo.getCloneUrl(), repo.getGitAccessToken(), releaseBranch, "unpublished");
                if (!archived) {
                    log.warn("Failed to archive release branch {} for repo {}", releaseBranch, repoId.value());
                }
            });
        }
        windowIterationPort.detach(ReleaseWindowId.of(windowId), IterationKey.of(iterationKey));
    }

    public List<WindowIteration> list(String windowId) {
        releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        return windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
    }

    public PageResult<WindowIteration> listPaged(String windowId, int page, int size) {
        releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        return windowIterationPort.listByWindowPaged(ReleaseWindowId.of(windowId), page, size);
    }

    @Transactional
    public void createReleaseBranchForIteration(String windowId, String iterationKeyStr) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        IterationKey iterationKey = IterationKey.of(iterationKeyStr);
        Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();
        Instant now = Instant.now(clock);

        String releaseBranch = "release/" + releaseWindow.getWindowKey();
        if (!branchRuleUseCase.isCompliant(releaseBranch)) {
            throw ValidationException.invalidParameter("branchName");
        }

        for (RepoId repoId : iteration.getRepos()) {
            try {
                CodeRepository repo = codeRepositoryPort.findById(repoId)
                        .orElseThrow(() -> NotFoundException.repository(repoId.value()));
                GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                boolean created = gitBranchPort.createBranch(
                        repo.getCloneUrl(), repo.getGitAccessToken(), releaseBranch, repo.getDefaultBranch());
                if (created) {
                    log.info("Created release branch {} for repo {}", releaseBranch, repoId.value());
                }
            } catch (Exception e) {
                log.error("Failed to create release branch for repo {}: {}", repoId.value(), e.getMessage());
            }
        }

        windowIterationPort.updateReleaseBranch(windowId, iterationKeyStr, releaseBranch, now);
    }

    @Transactional
    public void mergeFeatureToRelease(String windowId, String iterationKeyStr) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        IterationKey iterationKey = IterationKey.of(iterationKeyStr);
        Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();
        Instant now = Instant.now(clock);

        String releaseBranch = windowIterationPort.getReleaseBranch(windowId, iterationKeyStr);
        if (releaseBranch == null) {
            releaseBranch = "release/" + releaseWindow.getWindowKey();
        }

        for (RepoId repoId : iteration.getRepos()) {
            try {
                CodeRepository repo = codeRepositoryPort.findById(repoId)
                        .orElseThrow(() -> NotFoundException.repository(repoId.value()));
                GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                String gitToken = repo.getGitAccessToken();
                String repoUrl = repo.getCloneUrl();

                String featureBranch = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                        .map(info -> info.getFeatureBranch())
                        .orElse("feature/" + iterationKey.value());

                GitBranchPort.MergeResult mergeResult = gitBranchPort.mergeBranch(
                        repoUrl, gitToken, featureBranch, releaseBranch,
                        "Merge " + featureBranch + " to " + releaseBranch + " for iteration " + iteration.getName());

                if (mergeResult.status() == MergeStatus.SUCCESS) {
                    log.info("Merged feature branch {} to release branch {} for repo {}",
                            featureBranch, releaseBranch, repoId.value());
                }
            } catch (Exception e) {
                log.error("Failed to merge feature branch for repo {}: {}", repoId.value(), e.getMessage());
            }
        }

        windowIterationPort.updateLastMergeAt(windowId, iterationKeyStr, now);
    }
}
