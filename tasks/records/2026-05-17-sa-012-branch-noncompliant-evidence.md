# 2026-05-17 SA-012 Branch Noncompliant Evidence

## 任务

按项目台账当前 P1/P2 队列，补 SA-012「分支名不合规路径」后端/GitLab 强证据。

## 变更

- `scripts/acceptance/run-acceptance.sh` 升级到 v3.7，新增 5.5 分支名不合规证据段。
- 新证据段通过真实 GitLab 分支直查确认待检 `feature/<iterationKey>` 和 `release/<windowKey>` 存在。
- 脚本临时保存并禁用当前启用的 BranchRule，创建本轮专用严格规则，使用 `branch-rules/check` 确认待检分支不合规，触发冲突扫描断言 `BRANCH_NONCOMPLIANT`，最后删除临时规则并恢复原规则。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-012 最新状态。

## 验收

- 行为验收：验收脚本能输出 SA-012 5.5 的 GitLab 分支存在、BranchRule 不合规和 `BRANCH_NONCOMPLIANT` 冲突扫描证据。
- 自动化验证：`bash -n scripts/acceptance/run-acceptance.sh` 通过；`bash scripts/acceptance/run-acceptance.sh` 通过，77 PASS / 0 FAIL / 0 SKIP；`scripts/dev/static-scan-topn.sh 10` 通过，报告 `.ai/reports/static-scan/20260517-212217/summary.md`。
- 文档同步：场景矩阵、项目台账、任务记录。

## 后续

- SA-010/SA-011 继续补更多真实冲突类型后端/GitLab 强证据。
- SA-010/SA-016 继续补真实部分失败生成/重试和报告导出类扩展。
