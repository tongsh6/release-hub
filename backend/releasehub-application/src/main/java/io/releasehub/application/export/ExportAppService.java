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

    public String exportReleaseWindowMarkdown(String windowId) {
        ReleaseWindowReportView report = exportReleaseWindowReport(windowId);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Release Window Report: ").append(markdownText(report.windowKey())).append("\n\n");
        markdown.append("| Field | Value |\n");
        markdown.append("| --- | --- |\n");
        appendMarkdownRow(markdown, "Window ID", report.windowId());
        appendMarkdownRow(markdown, "Window Key", report.windowKey());
        appendMarkdownRow(markdown, "Name", report.name());
        appendMarkdownRow(markdown, "Status", report.status());
        appendMarkdownRow(markdown, "Group Code", report.groupCode());
        appendMarkdownRow(markdown, "Planned Release At", report.plannedReleaseAt());
        appendMarkdownRow(markdown, "Published At", report.publishedAt());
        appendMarkdownRow(markdown, "Run Count", String.valueOf(report.runCount()));
        appendMarkdownRow(markdown, "Item Count", String.valueOf(report.itemCount()));
        appendMarkdownRow(markdown, "Step Count", String.valueOf(report.stepCount()));

        markdown.append("\n## Result Counts\n\n");
        if (report.resultCounts().isEmpty()) {
            markdown.append("No final results recorded.\n");
        } else {
            markdown.append("| Result | Count |\n");
            markdown.append("| --- | ---: |\n");
            report.resultCounts().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> appendMarkdownRow(markdown, entry.getKey(), String.valueOf(entry.getValue())));
        }

        markdown.append("\n## Runs\n");
        for (ReleaseWindowReportView.RunReport run : report.runs()) {
            markdown.append("\n### ").append(markdownText(run.runId())).append("\n\n");
            markdown.append("| Type | Status | Operator | Started At | Finished At |\n");
            markdown.append("| --- | --- | --- | --- | --- |\n");
            markdown.append("| ")
                    .append(markdownCell(run.runType())).append(" | ")
                    .append(markdownCell(run.status())).append(" | ")
                    .append(markdownCell(run.operator())).append(" | ")
                    .append(markdownCell(run.startedAt())).append(" | ")
                    .append(markdownCell(run.finishedAt())).append(" |\n\n");
            markdown.append("| Repo | Iteration | Planned | Executed | Final Result | Step | Step Result | Message |\n");
            markdown.append("| --- | --- | ---: | ---: | --- | --- | --- | --- |\n");
            for (ReleaseWindowReportView.ItemReport item : run.items()) {
                if (item.steps().isEmpty()) {
                    appendMarkdownItemRow(markdown, item, null);
                    continue;
                }
                for (ReleaseWindowReportView.StepReport step : item.steps()) {
                    appendMarkdownItemRow(markdown, item, step);
                }
            }
        }
        return markdown.toString();
    }

    private void appendMarkdownRow(StringBuilder markdown, String field, String value) {
        markdown.append("| ")
                .append(markdownCell(field))
                .append(" | ")
                .append(markdownCell(value))
                .append(" |\n");
    }

    private void appendMarkdownItemRow(StringBuilder markdown,
                                       ReleaseWindowReportView.ItemReport item,
                                       ReleaseWindowReportView.StepReport step) {
        markdown.append("| ")
                .append(markdownCell(item.repo())).append(" | ")
                .append(markdownCell(item.iterationKey())).append(" | ")
                .append(item.plannedOrder()).append(" | ")
                .append(item.executedOrder()).append(" | ")
                .append(markdownCell(item.finalResult())).append(" | ")
                .append(markdownCell(step == null ? null : step.actionType())).append(" | ")
                .append(markdownCell(step == null ? null : step.result())).append(" | ")
                .append(markdownCell(step == null ? null : step.message())).append(" |\n");
    }

    private String markdownCell(String value) {
        return markdownText(value).replace("|", "\\|");
    }

    private String markdownText(String value) {
        return valueOrEmpty(value)
                .replace("\r", " ")
                .replace("\n", " ");
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
