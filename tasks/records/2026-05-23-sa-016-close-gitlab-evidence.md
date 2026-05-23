# SA-016 关闭窗口后 tag/merge/archive 真实 GitLab 收尾证据

## 背景

- SA-016 已覆盖关闭窗口、重复关闭幂等、关闭后关键操作禁止、收尾 Run 可见、CI 触发状态和发布报告制品包归档。
- 剩余风险是关闭后只看到 Run 步骤，而不能从真实 GitLab 侧复核 release 分支是否合并到默认分支、tag 是否存在、feature/release 分支是否归档。

## 改动

- 收尾编排顺序收敛为：版本记录、release 合并到默认分支、创建 tag、触发 CI、归档 feature 分支、归档 release 分支。
- 收尾编排使用 `WindowIteration.releaseBranch` 记录作为权威 release 分支，缺失时才回退到当前命名策略推导。
- RunItem 现在会留下两个 `ARCHIVE_BRANCH` 步骤，分别表示 feature 分支和 release 分支关闭后归档。
- `run-acceptance.sh` 增加 GitLab tag 查询 helper，并在 SA-016 段复核 merge to main commit、tag、feature/release 原分支删除和 `archive/released/...` 归档分支存在。
- 同步补强 SA-011 既有验收阻塞点：迭代仓库 setup 阶段如果 Git feature 分支创建遇到外部 I/O 异常，不再让迭代创建 500，而是保留迭代仓库版本记录，后续 attach/conflict 扫描继续产出 `GIT_UNAVAILABLE` 或 `GIT_PERMISSION_DENIED` 证据。
- OpenSpec、场景矩阵、项目台账和路线图已同步；SA-016 当前切片出队，下一 HEAD 转向 SA-002 存量数据清理动作人工复核闭环。

## 验证

```bash
mvn -pl releasehub-application -am -Dtest=RunAppServiceTest,IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `RunAppServiceTest` + `IterationAppServiceTest`：37 PASS / 0 FAIL / 0 SKIP。
- `run-acceptance.sh` 语法检查通过。
- 真实 GitLab 场景验收：169 PASS / 0 FAIL / 0 SKIP；SA-016 关闭后 merge/tag/feature archive/release archive 真实证据通过，SA-011 Git 权限不足/不可达探针也通过。
- 前端 typecheck、i18n、roadmap HEAD 检查和 `git diff --check` 通过。
- 静态扫描通过，报告：`.ai/reports/static-scan/20260523-150144/summary.md`。

## 非目标

- 不改变关闭幂等、关闭后挂载/版本更新拒绝、报告导出、CI 触发状态和 retry 语义。
- 不做 PDF 报告。
- 不做自动清理历史 GitLab 分支；关闭动作只处理当前窗口关联仓库的 feature/release 分支。
