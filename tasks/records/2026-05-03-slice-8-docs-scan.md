# Slice 8: 文档更新 + 静态扫描

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 8 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

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
| `docs/deployment.md` | 修改：profile 表更新 | Doc |
| `docs/README.md` | 修改：测试命令更新 | Doc |
| `.ai/reports/static-scan/` | 新建：扫描报告 | Report |

## 执行步骤

### Step 1: 更新 deployment.md
- Profile 表对齐：`test` / `e2e`（Mode A/B）/ `local` / `prd`
- 删除 `unitTest`、`gitlab-e2e-local`、`gitlab-e2e` 引用
- 新增 Mode A/B 说明

### Step 2: 更新 docs/README.md
- 测试命令：`mvn test` vs `mvn verify`、`pnpm test` vs `pnpm test:e2e` vs `pnpm test:pact`
- 覆盖率相关命令

### Step 3: 静态扫描
```bash
scripts/dev/static-scan-topn.sh 10
```
- 检查组报告，处理 TopN 问题
- 保留报告到 `.ai/reports/static-scan/`

### Step 4: VERIFY
- `grep -r "unitTest\|application-gitlab-e2e\|gitlab-e2e-local" docs/` 无残留
- SpotBugs 0 bugs
- 扫描报告已存档

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |
