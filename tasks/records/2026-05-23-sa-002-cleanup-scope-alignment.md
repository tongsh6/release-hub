# 2026-05-23 SA-002 验收脏数据报告与复核口径收敛

## 背景

SA-001 全量场景验收通过，但 SA-002 脏数据检测阶段报告大量历史 DRAFT 发布窗口残留。既有 `sa002-safe-cleanup.sh` dry-run 报告只输出 3 条动作，原因是脚本只按 Docker PostgreSQL 直查口径生成 DRAFT 动作，而全量验收的 DRAFT 窗口来自后端 API 可见数据。

这个差异会让用户误判存量噪声规模：全量验收看到很多 DRAFT 告警，但 dry-run 清理计划只给出少数复核动作。

## 范围

- DRAFT 发布窗口残留使用后端 API 口径生成复核动作。
- token 明文、BranchCreationMode、featureBranch、cloneUrl 和 branchCreated 等底层字段继续通过数据库只读审计。
- 报告同时输出应用 API 资产统计和数据库直查资产统计。
- 继续拒绝 `--execute`，不自动清库、不关闭窗口、不触碰 GitLab。

## 非目标

- 不执行任何自动清理。
- 不修改数据库或 GitLab 远端资源。
- 不改变发布窗口删除保护、关闭窗口、attach/detach 或分支归档语义。
- 不把历史 DRAFT 残留直接视为验收失败；它们仍是人工复核对象。

## 改动

- `scripts/acceptance/sa002-safe-cleanup.sh` 新增后端健康检查和管理员登录。
- dry-run 资产统计新增应用 API 口径，并保留数据库直查口径。
- DRAFT 发布窗口复核动作改为来自 `GET /api/v1/release-windows`。
- summary 报告显式说明两类资产统计来源，避免混淆用户可见数据与底层审计数据。
- `docs/openspec/specs/data-quality/spec.md` 增补 dry-run 口径对齐场景。

## 验证

```bash
bash -n scripts/acceptance/sa002-safe-cleanup.sh
scripts/acceptance/sa002-safe-cleanup.sh --report-dir .ai/reports/sa002-safe-cleanup/20260523-aligned-baseline
python3 - <<'PY'
import json, collections
path = ".ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/actions.jsonl"
counts = collections.Counter()
with open(path) as f:
    for line in f:
        d = json.loads(line)
        counts[d["riskType"]] += 1
print(dict(counts))
print("total", sum(counts.values()))
PY
scripts/acceptance/sa002-safe-cleanup.sh --execute
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- dry-run 报告生成：`.ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/summary.md`。
- 应用 API 资产统计：359 groups / 136 repos / 372 windows / 557 iterations / 554 runs。
- 数据库直查资产统计：10 groups / 8 repos / 6 windows / 8 iterations / 11 runs。
- 待复核动作：188 条。
- 风险分布：`DRAFT_WINDOW_REMAINS=187`、`ATTACH_BRANCH_NOT_CREATED=1`。
- `--execute` 继续拒绝。
- 静态扫描通过：`.ai/reports/static-scan/20260523-194658/summary.md`。

## 结论

SA-002 dry-run 清理报告已能解释全量验收暴露的历史 DRAFT 窗口残留告警，同时保留人工复核和应用层入口边界。当前路线图 HEAD 转向 SA-001 发布候选收口报告与下一阶段路线图。
