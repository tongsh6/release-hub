package io.releasehub.application.export;

import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ExportAppService {
    private final RunPort runPort;

    public String exportCsv(String runId) {
        Run run = runPort.findById(runId).orElseThrow();
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("runId,windowKey,repo,iterationKey,plannedOrder,executedOrder,stepType,stepResult,stepStart,stepEnd,message,finalResult");
        for (RunItem item : run.getItems()) {
            for (RunStep step : item.getSteps()) {
                joiner.add(String.join(",",
                        run.getId(),
                        item.getWindowKey(),
                        item.getRepo().value(),
                        item.getIterationKey().value(),
                        String.valueOf(item.getPlannedOrder()),
                        String.valueOf(item.getExecutedOrder()),
                        step.actionType().name(),
                        step.result().name(),
                        String.valueOf(step.startAt().toEpochMilli()),
                        String.valueOf(step.endAt().toEpochMilli()),
                        step.message(),
                        item.getFinalResult() == null ? "" : item.getFinalResult().name()
                ));
            }
        }
        return joiner.toString();
    }
}
