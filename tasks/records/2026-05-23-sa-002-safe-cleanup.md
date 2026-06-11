# 2026-05-23 SA-002 存量数据安全清理 dry-run

## 背景

SA-002 已通过全量验收脚本把 token 明文、BranchCreationMode、featureBranch、cloneUrl、DRAFT 残留等存量数据风险报告出来，但缺少独立入口把审计发现转成可复核、可追踪的清理动作清单。路线图要求默认 dry-run，不自动修改用户数据，不绕过业务约束。

## 范围

- 新增独立 SA-002 dry-run 清理报告脚本。
- 报告输出资产统计、BranchCreationMode 分布、Markdown 动作清单和 JSONL 动作清单。
- 动作字段固定包含资源类型、资源 ID、风险类型、建议动作和已执行标记。
- 明确拒绝 `--execute` 自动执行，避免直接改库或批量删除。
- 同步中文 OpenSpec、脚本索引、场景矩阵、项目台账和路线图。

## 非目标

- 不自动批量删除发布窗口、挂载关系或仓库。
- 不直接 UPDATE/DELETE 数据库来伪造修复完成。
- 不触碰真实 GitLab 远端分支或远端仓库内容。
- 不输出 token 明文。
- 不引入新的全局工具、系统级依赖或外部服务。

## 改动

- `scripts/acceptance/sa002-safe-cleanup.sh`：
  - 默认 dry-run，只读 `releasehub-postgres`。
  - 识别仓库 token 明文、Settings token 明文、BranchCreationMode 缺失/非法、featureBranch 缺失、cloneUrl 异常、DRAFT 发布窗口残留和 `window_iteration.branch_created=false`。
  - 生成 `summary.md`、`actions.md`、`actions.jsonl`。
  - `--execute` 直接拒绝，提示使用 dry-run 动作清单和应用层入口进行人工复核修复。
- `docs/openspec/specs/data-quality/spec.md` 新增存量数据安全清理契约。
- `scripts/README.md` 新增脚本索引。
- `scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 更新 SA-002 结论。

## 验证

```bash
bash -n scripts/acceptance/sa002-safe-cleanup.sh
scripts/acceptance/sa002-safe-cleanup.sh --report-dir .ai/reports/sa002-safe-cleanup/manual-verify
scripts/acceptance/sa002-safe-cleanup.sh --execute
```

结果：

- dry-run 报告生成成功：`.ai/reports/sa002-safe-cleanup/manual-verify/summary.md`、`actions.md`、`actions.jsonl`。
- 本地数据资产统计：Groups=10、Repos=8、Windows=6、Iterations=8、Runs=11。
- BranchCreationMode 分布：`AUTO=7`。
- 本地 dry-run 发现 3 条待复核动作：2 个 DRAFT 发布窗口残留、1 个 `window_iteration.branch_created=false`。
- `--execute` 按设计拒绝执行。

## 结论

SA-002 已从“审计可见”推进到“可生成独立 dry-run 清理计划”。当前阶段不做自动执行修复；下一路线图 HEAD 转向 SA-014 版本解析异常样本治理。
