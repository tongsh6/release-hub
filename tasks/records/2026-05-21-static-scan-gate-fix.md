# 静态扫描门禁脚本修复

日期：2026-05-21

## 背景

`scripts/dev/static-scan-topn.sh` 在后端 SpotBugs 阶段遇到 Maven 依赖解析失败时，会因为没有提取到 SpotBugs bug pattern 而在 summary 中误写为 `PASS (0 bugs)`。这会导致质量门禁证据不可信。

同时前端 ESLint warning 会被 summary 粗略标为 issues，但 TopN 不展示 warning 明细，报告信息不够一致。

## 实现范围

- 后端 SpotBugs 命令改为先执行 `mvn -DskipTests install`，确保 reactor 内部 `0.1.0-SNAPSHOT` 依赖先安装到本地仓库，再执行 SpotBugs。
- summary 增加 Maven/插件/依赖解析失败识别，扫描失败时标为 `FAIL (scan failed)`，不再误判为 0 bugs。
- ESLint 结果区分 errors 与 warnings，质量基线同步反映真实状态。
- TopN 支持展示扫描执行失败和 ESLint warning。
- 清理 `ConflictPanel.spec.ts` 既有 lint warning，使前端 lint 恢复干净。

## 验证

- `mvn -q -B -DskipTests install com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check`
  - PASS
- `pnpm run lint`
  - PASS
- `pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts src/views/branch-rule/__tests__/BranchRuleList.spec.ts`
  - 6 PASS / 0 FAIL
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-233416/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不调整 SpotBugs 规则集。
- 不改变 Maven 模块依赖结构。
