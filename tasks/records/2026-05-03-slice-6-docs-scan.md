# Slice 6: 文档更新 + 静态扫描

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 6 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 文档同步 + 静态扫描收尾 |
| 用户价值 | ✅ | 文档反映实际 profile、命令和 CI 流程 |
| 端到端路径 | ✅ | Docs → 全项目 |
| 单一目标 | ✅ | 只做文档更新和扫描 |
| 可独立验证 | ✅ | `grep` 文档无 stale 引用 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 依赖 Slice 1-5（完成后才能准确描述） |
| 风险收敛 | ✅ | 纯文档/扫描，无代码变更 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `docs/deployment.md` | 修改：profile 表更新 | Doc |
| `docs/README.md` | 修改：测试命令更新 | Doc |
| `.ai/reports/static-scan/` | 新建：静态扫描报告 | Report |

## 执行步骤

### Step 1: 更新 deployment.md
- Profile 表对齐实际：`test` / `e2e`（两种模式）/ `local` / `prd`
- 删 `unitTest`、`gitlab-e2e-local` 引用

### Step 2: 更新 docs/README.md
- 测试命令对齐：`mvn test` vs `mvn verify`、`pnpm test` vs `pnpm test:e2e`

### Step 3: 静态扫描
```bash
scripts/dev/static-scan-topn.sh 10
```
- 检查报告，处理 TopN 问题
- 保留报告到 `.ai/reports/static-scan/`

### Step 4: VERIFY
- `grep -r "unitTest\|gitlab-e2e-local\|gitlab-e2e" docs/` 无残留引用
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

## 静态扫描

**扫描命令**：
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| | | |

## 经验沉淀

- [ ] 不需要
- [ ] 已创建经验文档
- [ ] 已更新经验索引
