package io.releasehub.application.window;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DryPlanAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final GitLabPort gitLabPort;

    public List<DryPlanItemView> dryPlanByWindow(String windowId) {
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(windowId)).orElseThrow();
        List<WindowIteration> bindings = windowIterationPort.listByWindow(new ReleaseWindowId(windowId));
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));
        List<DryPlanItemView> views = new ArrayList<>();
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElseThrow();
            for (RepoId repoId : iteration.getRepos()) {
                long projectId = 0L;
                boolean featureExists = gitLabPort.branchExists(projectId, "feature/" + wi.getIterationKey().value());
                boolean releaseExists = gitLabPort.branchExists(projectId, "release/" + rw.getWindowKey());
                var mr = gitLabPort.ensureMrInfo(projectId, "feature/" + wi.getIterationKey().value(), "release/" + rw.getWindowKey()).orElse(new GitLabPort.MrInfo(false, false, null, null));
                var gate = gitLabPort.fetchGateSummary(projectId);
                views.add(new DryPlanItemView(
                        rw.getWindowKey(),
                        repoId.value(),
                        wi.getIterationKey().value(),
                        featureExists,
                        releaseExists,
                        mr.exists(),
                        mr.merged(),
                        mr.iid(),
                        mr.url(),
                        gate.protectedBranch(),
                        gate.approvalRequired(),
                        gate.pipelineGate(),
                        gate.permissionDenied()
                ));
            }
        }
        return views;
    }
}
