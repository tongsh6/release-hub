package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchCreationModeMigrationDryRunAppService {
    private static final Set<String> LEGAL_MODES = Set.of("AUTO", "NAMED", "EXISTING");

    private final BranchCreationModeMigrationDryRunPort port;

    public BranchCreationModeMigrationDryRunResult dryRun(BranchCreationModeMigrationDryRunCommand command) {
        if (command == null || isBlank(command.requestedBy())) {
            throw ValidationException.invalidParameter("requestedBy");
        }

        List<BranchCreationModeMigrationCandidateView> candidates = port.listIterationRepoBranchModes().stream()
                .map(this::toCandidate)
                .filter(candidate -> candidate != null)
                .toList();

        return new BranchCreationModeMigrationDryRunResult(
                command.requestedBy().trim(),
                command.sourceReport(),
                candidates.size(),
                count(candidates, "SAFE_AUTO_DEFAULTABLE"),
                count(candidates, "NORMALIZE_LEGAL_VALUE"),
                count(candidates, "MANUAL_MAPPING_REQUIRED"),
                count(candidates, "NOT_MIGRATABLE_IN_THIS_SERVICE"),
                false,
                candidates,
                markdown(command.requestedBy().trim(), command.sourceReport(), candidates));
    }

    private BranchCreationModeMigrationCandidateView toCandidate(BranchCreationModeMigrationDryRunPort.IterationRepoBranchModeRecord record) {
        String raw = record.branchCreationMode();
        String normalized = normalize(raw);
        String expectedAutoBranch = "feature/" + record.iterationKey();
        String featureBranch = record.featureBranch();

        if (!isBlank(raw) && LEGAL_MODES.contains(raw)) {
            return null;
        }

        if (isBlank(raw)) {
            if (expectedAutoBranch.equals(featureBranch)) {
                return candidate(record, "AUTO", "SAFE_AUTO_DEFAULTABLE",
                        "branchCreationMode 缺失且 featureBranch 等于 feature/{iterationKey}，可作为历史 AUTO 默认行为候选。",
                        false, true, null);
            }
            if (isBlank(featureBranch) || !featureBranch.startsWith("feature/")) {
                return candidate(record, null, "NOT_MIGRATABLE_IN_THIS_SERVICE",
                        "branchCreationMode 缺失，但 featureBranch 缺失或不在 feature/ 路径下。",
                        false, false, "缺少可保护 BranchCreationMode 语义的 featureBranch。");
            }
            return candidate(record, null, "MANUAL_MAPPING_REQUIRED",
                    "branchCreationMode 缺失，featureBranch 在 feature/ 路径下，但无法仅凭本地数据区分 NAMED 与 EXISTING。",
                    true, false, null);
        }

        if (LEGAL_MODES.contains(normalized)) {
            return candidate(record, normalized, "NORMALIZE_LEGAL_VALUE",
                    "branchCreationMode 可通过去空格和大写转换规范化为合法枚举值。",
                    false, true, null);
        }

        if (isBlank(featureBranch) || !featureBranch.startsWith("feature/")) {
            return candidate(record, null, "NOT_MIGRATABLE_IN_THIS_SERVICE",
                    "branchCreationMode 非法，且 featureBranch 缺失或不在 feature/ 路径下。",
                    false, false, "非法值需要人工业务处理，不能由本迁移服务推断。");
        }

        return candidate(record, null, "MANUAL_MAPPING_REQUIRED",
                "branchCreationMode 为未知值，featureBranch 在 feature/ 路径下，需要人工确认目标枚举。",
                true, false, null);
    }

    private BranchCreationModeMigrationCandidateView candidate(
            BranchCreationModeMigrationDryRunPort.IterationRepoBranchModeRecord record,
            String proposedMode,
            String classification,
            String inferenceReason,
            boolean requiresManualMapping,
            boolean executableAfterApproval,
            String rejectionReason) {
        String executionPlanDraft = executionPlanDraft(record, proposedMode, requiresManualMapping, executableAfterApproval);
        return new BranchCreationModeMigrationCandidateView(
                hash(record.iterationKey(), record.repoId(), record.branchCreationMode(), proposedMode, classification),
                record.iterationKey(),
                record.repoId(),
                record.branchCreationMode(),
                record.featureBranch(),
                proposedMode,
                classification,
                inferenceReason,
                requiresManualMapping,
                executableAfterApproval,
                executionPlanDraft,
                rejectionReason);
    }

    private String executionPlanDraft(BranchCreationModeMigrationDryRunPort.IterationRepoBranchModeRecord record,
                                      String proposedMode,
                                      boolean requiresManualMapping,
                                      boolean executableAfterApproval) {
        if (executableAfterApproval && proposedMode != null) {
            return "After explicit approval, set branchCreationMode to " + proposedMode
                    + " for iterationKey=" + record.iterationKey()
                    + ", repoId=" + record.repoId() + ".";
        }
        if (requiresManualMapping) {
            return "Manual mapping is required before an execution plan can be generated.";
        }
        return "No execution plan is available in this migration service.";
    }

    private int count(List<BranchCreationModeMigrationCandidateView> candidates, String classification) {
        return (int) candidates.stream()
                .filter(candidate -> classification.equals(candidate.classification()))
                .count();
    }

    private String markdown(String requestedBy, String sourceReport, List<BranchCreationModeMigrationCandidateView> candidates) {
        StringBuilder report = new StringBuilder();
        report.append("# BranchCreationMode 迁移 dry-run 报告\n\n");
        report.append("- requestedBy: ").append(requestedBy).append('\n');
        report.append("- sourceReport: ").append(sourceReport == null ? "" : sourceReport).append('\n');
        report.append("- executionPermitted: false\n");
        report.append("- totalCandidates: ").append(candidates.size()).append("\n\n");
        report.append("| iterationKey | repoId | currentMode | featureBranch | classification | proposedMode | requiresManualMapping | executableAfterApproval | executionPlanDraft | reason |\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (BranchCreationModeMigrationCandidateView candidate : candidates) {
            report.append("| ")
                    .append(md(candidate.iterationKey())).append(" | ")
                    .append(md(candidate.repoId())).append(" | ")
                    .append(md(candidate.currentModeRaw())).append(" | ")
                    .append(md(candidate.featureBranch())).append(" | ")
                    .append(md(candidate.classification())).append(" | ")
                    .append(md(candidate.proposedMode())).append(" | ")
                    .append(candidate.requiresManualMapping()).append(" | ")
                    .append(candidate.executableAfterApproval()).append(" | ")
                    .append(md(candidate.executionPlanDraft())).append(" | ")
                    .append(md(candidate.inferenceReason())).append(" |\n");
        }
        return report.toString();
    }

    private String md(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String hash(String iterationKey, String repoId, String currentModeRaw, String proposedMode, String classification) {
        String source = String.join("|",
                iterationKey == null ? "" : iterationKey,
                repoId == null ? "" : repoId,
                currentModeRaw == null ? "<NULL>" : currentModeRaw,
                proposedMode == null ? "" : proposedMode,
                classification == null ? "" : classification);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
