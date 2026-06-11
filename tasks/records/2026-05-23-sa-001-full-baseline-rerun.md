# 2026-05-23 SA-001 全量场景验收基线复跑与发布候选判定

## 背景

Phase 2 存量缺口池完成清账后，需要回到全量场景验收和静态扫描，证明近期 focused slices 不是局部脚本通过，而是在真实后端、真实 GitLab 和持久化数据链路中仍成立。

## 范围

- 运行全量场景验收脚本。
- 运行静态扫描 TopN。
- 更新场景矩阵、项目台账和执行路线图。
- 若发现新的产品质量缺口，设置下一唯一 HEAD。

## 验证

```bash
bash scripts/acceptance/run-acceptance.sh
bash scripts/dev/static-scan-topn.sh 10
bash scripts/dev/check-roadmap.sh
git diff --check
```

结果：

- 全量场景验收：170 PASS / 0 FAIL / 0 SKIP。
- 数据资产：359 groups / 134 repos / 358 windows / 541 iterations / 532 runs。
- 覆盖链路：GitLab Settings 重启持久化、三层分组叶子资源约束、Attach/Detach release 分支创建与归档、冲突强证据、部分失败 retry、版本更新单模块/多模块/Gradle/批量部分失败、关闭后 GitLab merge/tag/archive 收尾、分支创建模式。
- 静态扫描：SpotBugs 0 bugs、frontend lint PASS、frontend typecheck PASS。
- 静态扫描报告：`.ai/reports/static-scan/20260523-193829/summary.md`。

## 新暴露问题

全量验收通过，但 SA-002 脏数据检测阶段报告大量历史 DRAFT 窗口残留和 1 条 `branch_created=false` 记录。它们当前不阻塞验收，但与 `sa002-safe-cleanup.sh` dry-run 历史报告的数量口径不一致，容易让用户误判存量风险规模。

## 结论

近期 focused slices 回到整体产品链路后仍成立。下一路线图 HEAD 转向 SA-002 验收脏数据报告与复核口径收敛。
