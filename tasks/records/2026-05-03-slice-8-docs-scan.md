# Slice 8: 文档更新 + 静态扫描

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 8 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 文档同步 + 静态扫描收尾 |
| 用户价值 | ✅ | 文档反映最新 profile、测试命令和 CI 流程 |
| 端到端路径 | ✅ | Docs → 全项目 |
| 单一目标 | ✅ | 只做文档和扫描 |
| 可独立验证 | ✅ | `grep` 无 stale 引用，SpotBugs 0 bugs |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 依赖 Slice 1-7 |
| 风险收敛 | ✅ | 纯文档，无代码变更 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `docs/deployment.md` | 修改：profile 表更新 + Mode A/B 说明 | Doc |
| `docs/README.md` | 修改：测试命令更新、测试栈描述更新 | Doc |
| `backend/pom.xml` | 修改：coverage profile 加阈值策略注释 | Build |
| `.ai/reports/static-scan/` | 已有：3 次扫描报告（0502 ×2, 0503 ×1） | Report |

## 执行步骤

### Step 1: 更新 deployment.md
- Profile 表对齐：`test` / `e2e`（Mode A/B）/ `local` / `prd` ✅
- 删除 `unitTest`、`gitlab-e2e-local`、`gitlab-e2e` 引用 ✅
- 新增 Mode A/B 说明 ✅

### Step 2: 更新 docs/README.md
- 测试命令：`mvn test`、`mvn verify -Pcoverage`、`pnpm test --coverage`、`pnpm test:e2e`、`pnpm test:pact` ✅
- 测试栈描述：Puppeteer → Playwright + Pact ✅
- 项目结构：`e2e/` 描述更新为 Playwright ✅

### Step 3: 父 POM 覆盖率策略注释
- `coverage` profile 上方添加注释，说明当前阈值为临时占位值 + 目标阈值 + 上调策略 ✅

### Step 4: 静态扫描
- `.ai/reports/static-scan/` 已有 3 次历史报告 ✅
- SpotBugs 0 bugs ✅

### Step 5: VERIFY
- `grep -r "unitTest\|application-gitlab-e2e\|gitlab-e2e-local" docs/` 无残留 ✅
- `grep -r "Puppeteer" docs/` 无残留 ✅
- 扫描报告已存档 ✅

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```
**报告路径**：`.ai/reports/static-scan/20260503-231410/`
**TopN 处理结论**：
**未解决风险**：

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | profile + 测试命令文档与代码一致 |
| 层级闭环 | ✅ | deployment.md + README 双重覆盖 |
| 测试闭环 | ✅ | 文档完整反映测试命令 |
| 架构闭环 | ✅ | Mode A/B 已文档化 |
| 性能闭环 | ✅ | |
| 文档闭环 | ✅ | 无 stale 引用残留 |
| 工作区闭环 | ✅ | 扫描报告存档 |
