package io.releasehub.application.dataquality;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CleanupReviewResult(
        String reviewer,
        String sourceReport,
        int total,
        int accepted,
        int pending,
        int rejected,
        List<AssetBoundarySummary> assetBoundaries,
        List<AssetScopeCount> assetScopeCounts,
        List<CleanupActionReview> actions
) {
    public CleanupReviewResult(String reviewer, String sourceReport, int total, int accepted, int pending, int rejected,
                               List<CleanupActionReview> actions) {
        this(reviewer, sourceReport, total, accepted, pending, rejected, defaultAssetBoundaries(),
                countAssetScopes(actions), actions);
    }

    public CleanupReviewResult {
        assetBoundaries = List.copyOf(assetBoundaries == null ? List.of() : assetBoundaries);
        assetScopeCounts = List.copyOf(assetScopeCounts == null ? List.of() : assetScopeCounts);
        actions = List.copyOf(actions);
    }

    private static List<AssetBoundarySummary> defaultAssetBoundaries() {
        return List.of(
                new AssetBoundarySummary(
                        "API_VISIBLE_ASSETS",
                        "应用 API 可见资产",
                        "来自应用 API 的用户可见资源统计，代表页面和业务操作会看到的资产范围。",
                        true,
                        false),
                new AssetBoundarySummary(
                        "DB_AUDIT_ASSETS",
                        "数据库审计资产",
                        "来自数据库只读审计的底层记录统计，用于发现字段级风险，不等同于用户可见风险。",
                        false,
                        false),
                new AssetBoundarySummary(
                        "REVIEW_QUEUE_ACTIONS",
                        "复核队列动作",
                        "由 dry-run 风险识别生成并进入人工复核的动作集合，是本入口的处理范围。",
                        true,
                        true)
        );
    }

    private static List<AssetScopeCount> countAssetScopes(List<CleanupActionReview> actions) {
        Map<String, Integer> counts = new TreeMap<>();
        for (CleanupActionReview action : actions == null ? List.<CleanupActionReview>of() : actions) {
            String scope = action.assetScope() == null || action.assetScope().isBlank()
                    ? "UNSPECIFIED"
                    : action.assetScope();
            counts.merge(scope, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new AssetScopeCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record AssetBoundarySummary(
            String key,
            String label,
            String description,
            boolean userVisible,
            boolean manualReviewCandidate
    ) {
    }

    public record AssetScopeCount(
            String assetScope,
            int count
    ) {
    }
}
