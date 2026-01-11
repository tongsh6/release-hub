package io.releasehub.application.run;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.window.WindowIterationPort;
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
}
