package io.releasehub.application.export;

import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ExportAppService {
    private final RunPort runPort;
    private final ReleaseWindowPort releaseWindowPort;

    public String exportCsv(String runId) {
        Run run = runPort.findById(runId).orElseThrow();
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("runId,windowKey,repo,iterationKey,plannedOrder,executedOrder,stepType,stepResult,stepStart,stepEnd,message,finalResult");
        for (RunItem item : run.getItems()) {
            for (RunStep step : item.getSteps()) {
                joiner.add(csvRow(List.of(
                        run.getId().value(),
                        item.getWindowKey(),
                        item.getRepo().value(),
                        item.getIterationKey() == null ? "" : item.getIterationKey().value(),
                        String.valueOf(item.getPlannedOrder()),
                        String.valueOf(item.getExecutedOrder()),
                        step.actionType().name(),
                        step.result().name(),
                        step.startAt() == null ? "" : String.valueOf(step.startAt().toEpochMilli()),
                        step.endAt() == null ? "" : String.valueOf(step.endAt().toEpochMilli()),
                        valueOrEmpty(step.message()),
                        item.getFinalResult() == null ? "" : item.getFinalResult().name()
                )));
            }
        }
        return joiner.toString();
    }

    public ReleaseWindowReportView exportReleaseWindowReport(String windowId) {
        ReleaseWindow window = releaseWindowPort.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
        List<Run> runs = runPort.findByWindowKey(window.getWindowKey());
        return ReleaseWindowReportView.from(window, runs);
    }

    public String exportReleaseWindowCsv(String windowId) {
        ReleaseWindowReportView report = exportReleaseWindowReport(windowId);
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("windowId,windowKey,runId,runType,runStatus,repo,iterationKey,finalResult,stepType,stepResult,stepStart,stepEnd,message");
        for (ReleaseWindowReportView.RunReport run : report.runs()) {
            for (ReleaseWindowReportView.ItemReport item : run.items()) {
                if (item.steps().isEmpty()) {
                    joiner.add(csvRow(List.of(
                            report.windowId(),
                            report.windowKey(),
                            run.runId(),
                            run.runType(),
                            run.status(),
                            valueOrEmpty(item.repo()),
                            valueOrEmpty(item.iterationKey()),
                            valueOrEmpty(item.finalResult()),
                            "",
                            "",
                            "",
                            "",
                            ""
                    )));
                    continue;
                }
                for (ReleaseWindowReportView.StepReport step : item.steps()) {
                    joiner.add(csvRow(List.of(
                            report.windowId(),
                            report.windowKey(),
                            run.runId(),
                            run.runType(),
                            run.status(),
                            valueOrEmpty(item.repo()),
                            valueOrEmpty(item.iterationKey()),
                            valueOrEmpty(item.finalResult()),
                            step.actionType(),
                            step.result(),
                            valueOrEmpty(step.startAt()),
                            valueOrEmpty(step.endAt()),
                            valueOrEmpty(step.message())
                    )));
                }
            }
        }
        return joiner.toString();
    }

    private String csvRow(List<String> values) {
        return values.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(","));
    }

    private String csvCell(String value) {
        String safe = valueOrEmpty(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
