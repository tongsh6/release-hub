# 2026-05-17 SA-011 Merge Conflict Evidence

## 任务

按项目台账当前 P1 队列，补 SA-011 `MERGE_CONFLICT` 真实后端/GitLab 强证据。

## 变更

- `scripts/acceptance/run-acceptance.sh` 升级到 v3.8，新增 5.6 `MERGE_CONFLICT` 证据段。
- 新证据段在真实 GitLab 上创建本轮唯一 feature/release 分支，并分别向 `pom.xml` 写入不同内容制造真实合并冲突。
- 迭代使用 `EXISTING` 模式关联 feature 分支，attach 后保留 `MERGE_BLOCKED` Run 证据，冲突扫描断言 `MERGE_CONFLICT`。
- 清理静态扫描暴露的 4 个既有前端 ESLint warning，使最终质量报告无 TopN 问题。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-011 最新状态。

## 验收

- 行为验收：验收脚本输出 GitLab 分支存在、冲突提交写入、Attach/Run 合并阻断和 `MERGE_CONFLICT` 冲突扫描证据。
- 自动化验证：`bash -n scripts/acceptance/run-acceptance.sh` 通过；`bash scripts/acceptance/run-acceptance.sh` 通过，88 PASS / 0 FAIL / 0 SKIP；`pnpm --dir frontend -s lint` 通过；`scripts/dev/static-scan-topn.sh 10` 通过，报告 `.ai/reports/static-scan/20260517-214012/summary.md`。
- 文档同步：场景矩阵、项目台账、任务记录。

## 后续

- SA-010/SA-011 继续补更多真实冲突类型后端/GitLab 强证据和部分失败重试。
- SA-016 继续补部分失败重试和发布报告导出类扩展。
